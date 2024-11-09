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
import java.util.Base64;
import java.util.Map;
import java.util.Set;

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
            Transform the following text into a highly detailed and vivid image description suitable for the Flux image generation model.
            The description shall be in English language, and less than 1000 characters long.
            Expand on the visual elements by incorporating rich descriptive language, specifying aspects like colors, lighting, textures,
            atmosphere, and artistic style to create a captivating and intricate image.
            The goal is to generate a prompt that will result in a detailed picture that captures the essence and mood of the given text as
            precise as possible.
            
            Text: "%s"
            """;

    private final SimpleAiService simpleAiService;
    private final String apiKey;
    private final String imageSaveDirectory;
    private final String imageUrlPrefix;

    public ImageCommandHandler(
            SimpleAiService simpleAiService,
            @Value("${together.apiKey}") String apiKey,
            @Value("${image.saveDirectory}") String imageSaveDirectory,
            @Value("${image.urlPrefix}") String imageUrlPrefix) {
        this.simpleAiService = simpleAiService;
        this.apiKey = apiKey;
        this.imageSaveDirectory = imageSaveDirectory;
        this.imageUrlPrefix = imageUrlPrefix;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_IMAGE, CMD_AIIMAGE);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine().ifPresentOrElse(
                prompt -> generateImage(command, prompt),
                () -> command.respond(CMD_IMAGE.getUsage()));
        return true;
    }

    private void generateImage(CommandEvent command, String prompt) {
        // If requested, generate an image prompt using LLM
        String imagePrompt = prompt;
        if (CMD_AIIMAGE.equals(command.getCommand())) {
            String llmPrompt = AI_IMAGE_PROMPT_TEMPLATE.replace("\n", " ").formatted(prompt);
            imagePrompt = simpleAiService.query(llmPrompt);
        }

        // Build the JSON request body
        Map<String, Object> requestBody = Map.of(
                "model", "black-forest-labs/FLUX.1-schnell",
                "prompt", prompt,
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
        final String fOriginalPrompt = prompt;

        HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> processResponse(command, response, fImagePrompt, fOriginalPrompt))
                .exceptionally(e -> {
                    command.respond("Error generating image: " + e.getMessage());
                    return null;
                });
    }

    private void processResponse(CommandEvent command, HttpResponse<String> response, String imagePrompt, String originalPrompt) {
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
                imageBytes = XmpTool.addPrompt(imageBytes, imagePrompt, originalPrompt);

                // Generate a unique file name
                String fileName = "i" + System.currentTimeMillis() + ".jpg";
                File imageFile = Paths.get(imageSaveDirectory, fileName).toFile();

                // Save the image to disk
                try (OutputStream os = new FileOutputStream(imageFile)) {
                    os.write(imageBytes);
                }

                // Construct the image URL
                String imageUrl = imageUrlPrefix + fileName;

                // Respond with the image URL
                command.respond("Image generated: " + imageUrl);

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
            command.respond("Error generating image: " + response.body());
        }
    }
}
