package de.throughput.ircbot.handler;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Command handler for generating images using the Together.ai API.
 */
@Component
public class ImageCommandHandler implements CommandHandler {

    private static final Command CMD_IMAGE = new Command("image", "image <prompt> - generate an image from the given prompt");
    private static final Command CMD_AIIMAGE = new Command("aiimage",
            "aiimage <prompt> - generate an image prompt from the given prompt using an LLM, then generate an image from the generated prompt");
    private static final String API_URL = "https://api.together.xyz/v1/images/generations";

    private static final String AI_IMAGE_PROMPT_TEMPLATE = """
            Based on this input: "%s", create a prompt for the image generation model FLUX.1 [schnell]
            that includes subject, material i.e. medium or rendering style, artistic style, artist influence, details
            such as sharpness, color, lighting and additional elements in under 500 characters and in concise, natural,
            descriptive language, not as a list of those properties. The prompt shall not repeat the input, and it shall not
            describe feelings that are invoked or anything but a specific description of the image. Reply with just the prompt.
            """;

    private static final String AI_IMAGE_TITLE_TEMPLATE = """
            Based on the following prompt for an image generation model, create a (preferably short) title for the image. Reply with just the title.
            Prompt: "%s"
            """;

    public static final String MODEL_NAME = "black-forest-labs/FLUX.1-schnell-Free";

    private static final int MAX_QUEUE_SIZE = 5;

    private final SimpleAiService simpleAiService;
    private final String apiKey;
    private final String imageSaveDirectory;
    private final String imageUrlPrefix;
    private final long cooldownSeconds;
    private final ScheduledExecutorService scheduler;
    private final Object cooldownLock = new Object();
    private Instant nextAvailableTime = Instant.EPOCH;
    private final Deque<ImageRequest> requestQueue = new ArrayDeque<>();
    private boolean queueWorkerScheduled = false;

    private static final long COOLDOWN_BUFFER_SECONDS = 5;

    public ImageCommandHandler(
            SimpleAiService simpleAiService,
            @Value("${together.apiKey}") String apiKey,
            @Value("${image.saveDirectory}") String imageSaveDirectory,
            @Value("${image.urlPrefix}") String imageUrlPrefix,
            @Value("${image.model.cooldown.seconds:100}") long cooldownSeconds) {
        this.simpleAiService = simpleAiService;
        this.apiKey = apiKey;
        this.imageSaveDirectory = imageSaveDirectory;
        this.imageUrlPrefix = imageUrlPrefix;
        this.cooldownSeconds = cooldownSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("image-command-handler");
            return thread;
        });
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_IMAGE, CMD_AIIMAGE);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine().ifPresentOrElse(
                prompt -> handleImageRequest(command, prompt),
                () -> command.respond(command.getCommand().getUsage()));
        return true;
    }

    private void handleImageRequest(CommandEvent command, String prompt) {
        boolean executeImmediately = false;
        long waitSeconds = 0;
        boolean addedToQueue = false;
        boolean queueFull = false;
        ImageRequest targetRequest = null;
        Instant now = Instant.now();

        synchronized (cooldownLock) {
            if (requestQueue.isEmpty() && !now.isBefore(nextAvailableTime)) {
                executeImmediately = true;
                nextAvailableTime = now.plusSeconds(cooldownSeconds);
            } else {
                String requestKey = createRequestKey(command, prompt);
                targetRequest = findQueuedRequest(requestKey);
                if (targetRequest == null) {
                    if (requestQueue.size() >= MAX_QUEUE_SIZE) {
                        queueFull = true;
                    } else {
                        Instant scheduledTime = calculateScheduledTimeForNewRequest(now);
                        targetRequest = new ImageRequest(command, prompt, requestKey, scheduledTime);
                        requestQueue.addLast(targetRequest);
                        addedToQueue = true;
                    }
                }
                if (!queueFull && !requestQueue.isEmpty() && !queueWorkerScheduled) {
                    recalculateQueuedSchedule(now);
                }
                if (!queueFull && targetRequest != null) {
                    waitSeconds = calculateWaitSeconds(now, targetRequest.getScheduledTime());
                }
            }
        }

        if (queueFull) {
            command.respond("Image request queue is full. Please try again later.");
            return;
        }

        if (executeImmediately) {
            executeImageGeneration(command, prompt);
            return;
        }

        command.respond("Image generation request will be executed in " + waitSeconds
                + " seconds due to cooldown.");

        if (addedToQueue) {
            scheduleQueueWorker();
        }
    }

    private String createRequestKey(CommandEvent commandEvent, String prompt) {
        return commandEvent.getCommand().getCommand() + "|" + prompt;
    }

    private ImageRequest findQueuedRequest(String key) {
        for (ImageRequest request : requestQueue) {
            if (request.getKey().equals(key)) {
                return request;
            }
        }
        return null;
    }

    private Instant calculateScheduledTimeForNewRequest(Instant now) {
        if (requestQueue.isEmpty()) {
            if (now.isBefore(nextAvailableTime)) {
                return nextAvailableTime.plusSeconds(COOLDOWN_BUFFER_SECONDS);
            }
            return now;
        }
        Instant lastScheduled = requestQueue.peekLast().getScheduledTime();
        return lastScheduled
                .plusSeconds(cooldownSeconds)
                .plusSeconds(COOLDOWN_BUFFER_SECONDS);
    }

    private long calculateWaitSeconds(Instant now, Instant scheduledTime) {
        long delayMillis = Math.max(0, Duration.between(now, scheduledTime).toMillis());
        long waitSeconds = delayMillis / 1000;
        if (delayMillis % 1000 != 0) {
            waitSeconds += 1;
        }
        if (waitSeconds == 0) {
            waitSeconds = 1;
        }
        return waitSeconds;
    }

    private void recalculateQueuedSchedule(Instant referenceTime) {
        if (requestQueue.isEmpty()) {
            return;
        }
        Instant scheduledTime = referenceTime;
        if (referenceTime.isBefore(nextAvailableTime)) {
            scheduledTime = nextAvailableTime.plusSeconds(COOLDOWN_BUFFER_SECONDS);
        }
        Instant nextStart = scheduledTime;
        for (ImageRequest queued : requestQueue) {
            queued.setScheduledTime(nextStart);
            nextStart = nextStart
                    .plusSeconds(cooldownSeconds)
                    .plusSeconds(COOLDOWN_BUFFER_SECONDS);
        }
    }

    private void scheduleQueueWorker() {
        long delayMillis;
        synchronized (cooldownLock) {
            if (queueWorkerScheduled || requestQueue.isEmpty()) {
                return;
            }
            ImageRequest nextRequest = requestQueue.peekFirst();
            if (nextRequest == null) {
                return;
            }
            delayMillis = Math.max(0, Duration.between(Instant.now(), nextRequest.getScheduledTime()).toMillis());
            queueWorkerScheduled = true;
        }

        scheduler.schedule(this::processQueue, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void processQueue() {
        ImageRequest request;
        synchronized (cooldownLock) {
            request = requestQueue.pollFirst();
            queueWorkerScheduled = false;
        }

        if (request == null) {
            return;
        }

        executeImageGeneration(request.getCommandEvent(), request.getPrompt());

        synchronized (cooldownLock) {
            if (!requestQueue.isEmpty()) {
                recalculateQueuedSchedule(Instant.now());
            }
        }

        scheduleQueueWorker();
    }

    private void executeImageGeneration(CommandEvent command, String prompt) {
        Instant generationStart = Instant.now();
        synchronized (cooldownLock) {
            Instant potentialNext = generationStart.plusSeconds(cooldownSeconds);
            if (nextAvailableTime.isBefore(potentialNext)) {
                nextAvailableTime = potentialNext;
            }
        }

        String imagePrompt = prompt;
        String title = null;
        // If requested, generate an image prompt using LLM
        if (CMD_AIIMAGE.equals(command.getCommand())) {
            String llmPrompt = AI_IMAGE_PROMPT_TEMPLATE.replace("\n", " ").formatted(prompt);
            imagePrompt = simpleAiService.query(llmPrompt);
            String titlePrompt = AI_IMAGE_TITLE_TEMPLATE.replace("\n", " ").formatted(prompt + ": " + imagePrompt);
            title = simpleAiService.query(titlePrompt);
        } else {
            String titlePrompt = AI_IMAGE_TITLE_TEMPLATE.replace("\n", " ").formatted(prompt);
            title = simpleAiService.query(titlePrompt);
        }
        if (title != null) {
            title = title.replaceAll("^\"|\"$", "");
        }

        // Build the JSON request body
        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "prompt", imagePrompt,
                "width", 1024,
                "height", 768,
                "steps", 4,
                "n", 1,
                "response_format", "b64_json"
        );

        Gson gson = new Gson();
        String json = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        final String fImagePrompt = imagePrompt;
        final String fImageTitle = title;
        final String fOriginalPrompt = prompt;

        HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> processResponse(command, response, fImagePrompt, fImageTitle, fOriginalPrompt))
                .exceptionally(e -> {
                    String message = e.getMessage();
                    message = message != null ? message.replaceAll("\n", " ") : "Unknown error";
                    command.respond("Error generating image: " + message);
                    return null;
                });
    }

    private void processResponse(CommandEvent command, HttpResponse<String> response, String imagePrompt, String imageTitle, String originalPrompt) {
        if (response.statusCode() == 200) {
            try {
                // Parse the response
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> responseBody = gson.fromJson(response.body(), type);

                // Extract the base64-encoded image
                Map<String, Object> dataEntry = (Map<String, Object>) ((java.util.List<Object>) responseBody.get("data")).get(0);
                String b64Json = (String) dataEntry.get("b64_json");

                // Decode the image
                byte[] imageBytes = Base64.getDecoder().decode(b64Json);

                // Add the prompt as description
                imageBytes = XmpTool.addMetadata(imageBytes, imageTitle, imagePrompt, originalPrompt);

                // Generate a unique file name
                long imageId = System.currentTimeMillis();
                String fileName = "i" + imageId + ".jpg";
                File imageFile = Paths.get(imageSaveDirectory, fileName).toFile();

                // Save the image to disk
                try (OutputStream os = new FileOutputStream(imageFile)) {
                    os.write(imageBytes);
                }

                // Construct the image URL (link to gallery)
                String imageUrl = imageUrlPrefix + imageId;

                // Respond with the image URL
                String message = imageTitle != null ? imageTitle : "Image generated";
                command.respond(message + ": " + imageUrl);

            } catch (Exception e) {
                command.respond("Error processing image response: " + e.getMessage());
            }
        } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
            // Try to parse the error response
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> responseBody = gson.fromJson(response.body(), type);

                Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
                if (error != null) {
                    String errorType = (String) error.get("type");
                    String errorMessage = (String) error.get("message");
                    command.respond(errorType + ": " + errorMessage);
                } else {
                    // If the error object is missing, respond with the status code
                    command.respond("Error generating image: " + response.statusCode());
                }
            } catch (Exception e) {
                // Failed to parse the error response
                command.respond("Error generating image: " + response.statusCode());
            }
        } else {
            command.respond("Error generating image: " + response.statusCode());
        }
    }

    private static class ImageRequest {
        private final CommandEvent commandEvent;
        private final String prompt;
        private final String key;
        private Instant scheduledTime;

        ImageRequest(CommandEvent commandEvent, String prompt, String key, Instant scheduledTime) {
            this.commandEvent = commandEvent;
            this.prompt = prompt;
            this.key = key;
            this.scheduledTime = scheduledTime;
        }

        public CommandEvent getCommandEvent() {
            return commandEvent;
        }

        public String getPrompt() {
            return prompt;
        }

        public String getKey() {
            return key;
        }

        public Instant getScheduledTime() {
            return scheduledTime;
        }

        public void setScheduledTime(Instant scheduledTime) {
            this.scheduledTime = scheduledTime;
        }
    }
}
