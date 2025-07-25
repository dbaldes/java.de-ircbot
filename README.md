# DrMockIt - the #java.de IRC bot

This is the source code of DrMockIt, a [pircbotx](https://github.com/pircbotx/pircbotx)-based IRC bot which 
lingers in  `#java.de` on the [libera](https://libera.chat) IRC network. DrMockIt is the successor of 
DrRockIt, a former perl-based bot; some of the old bot's data and functions were incorporated into the 
new bot. 

Besides providing useful services to the users of this and other channels, this project serves as a test 
bed for trying out new technologies and APIs.

The bot is implemented as [spring boot](https://spring.io/projects/spring-boot) application using Java 21 
and [lombok](https://projectlombok.org/), and deployed on a vps as [docker](https://www.docker.com/) container. 
GitHub Actions were used to implement a CI/CD pipeline that runs integration tests on 
[PostgreSQL](https://www.postgresql.org/) and [InspIRCd](https://www.inspircd.org/), and automatically deploys the bot to 
production when changes are pushed to the master branch and the tests are successful.

## Features

- The bot will respond to messages if directly addressed by its nick, using the OpenAI chat completion 
  API and the `gpt-4o-mini` model. The bot keeps a context of 20 messages during conversation. A system prompt
  is loaded from a configuration file, giving the bot its unique personality. Note: only messages directly
  addressed to the bot, as well as its own responses to those, are added to the context and thus sent to 
  OpenAI.
- When URLs are posted to the channel, the bot will try to look them up and post a title and possibly a 
  preview of the content. Apart from plain HTML parsing, there are some special handlers for certain URLs 
  such as YouTube URLs (this one uses the YouTube API to retrieve information on the video).
- If the channel is active, the bot will occasionally post encouraging comments to the channel that have
  been added by users using the `!addslogan` command.
- The bot keeps track of when users where last seen on the channel (meaning when they last said something, 
  and what they said). This information can be queried using the `!seen` command.
- The bot stores facts that are told on the channel. A fact is detected when someone says 
  `<something> is/are <something>`. If `<something>` is later mentioned on its own, the bot will post
  any facts it knows about that thing.

This list is not exhaustive.

## Available Commands

Commands can start with either `!` or `*`. Commands can be abbreviated by just using one or more of its
starting letters, e.g. `!cr` instead of `!crypto`. If the short command is ambiguous, the bot will tell you.

### avherald
- **Usage**: `!avherald <search query>`
- **Description**: Searches for aviation incidents on The Aviation Herald.

### flight
- **Usage**: `!flight <flight number>`
- **Description**: Provides links to flight tracking information for the specified flight number.

### aircraft
- **Usage**: `!aircraft <registration>`
- **Description**: Provides a link to flight tracking information for the specified aircraft registration.

### airport
- **Usage**: `!airport <IATA or ICAO code>`
- **Description**: Provides links to information about the specified airport.

### ath
- **Usage**: `!ath [<id>]`
- **Description**: Shows the all-time-high of a cryptocurrency by CoinGecko ID (defaults to bitcoin) in USD and EUR with dates.

### crypto
- **Usage**: `crypto [<amount>] <symbols> [in <currency>]`
- **Description**: Gets price information on cryptocurrencies. Defaults to USD and amount to 1.

### tlast
- **Usage**: `!tlast`
- **Description**: Gets the latest Bitcoin price in USD, if the bot `gribble` isn't present.

### estr
- **Usage**: `!estr`
- **Description**: Gets the current Euro short term rate.

### forget
- **Usage**: `!forget <key>`
- **Description**: Forgets a specified fact.

### flip
- **Usage**: `!flip <text>`
- **Description**: Flips `<text>` over in rage.

### lagerfeld
- **Usage**: `!lagerfeld <text>`
- **Description**: Responds with an AI-generated Karl Lagerfeld quote.

### aroma
- **Usage**: `!aroma <description>`
- **Description**: Generates an aroma description based on the given hint.

### aireset
- **Usage**: `!aireset`
- **Description**: Deletes the current context for the channel and reloads the system prompt.

### quote
- **Usage**: `!quote <nick>`
- **Description**: Records a quote from the specified nick.

### quoter
- **Usage**: `!quoter <nick>`
- **Description**: Retrieves a random quote from the specified nick.

### news
- **Usage**: `!news [topic]`
- **Description**: Shows a short summary of current news, optionally focusing on a topic.

### remindme
- **Usage**: `remindme <when>: <text>`
- **Description**: Sets a reminder. `<when>` can be a date (YYYY-MM-DD) or a duration (e.g., '1 year', '3 days').

### roulette
- **Usage**: `!roulette spin` or `!roulette`
- **Description**: Plays a game of Russian roulette.

### seen
- **Usage**: `!seen <nick>`
- **Description**: Checks when the specified nick was last seen.

### slogan
- **Usage**: `!slogan`
- **Description**: Enhances morale by shouting a slogan.

### addslogan
- **Usage**: `!addslogan <slogan>`
- **Description**: Adds a slogan.

### rmslogan
- **Usage**: `!rmslogan <slogan>`
- **Description**: Removes a slogan.

### stock
- **Usage**: `!stock <symbols>`
- **Description**: Gets price information on stock symbols.

### price
- **Usage**: `!price <symbols>`
- **Description**: Gets real-time price information on stock symbols.

### fx
- **Usage**: `!fx <symbols> [YYYY-MM-DD]`
- **Description**: Gets currency exchange rates.

### image
- **Usage**: `!image <prompt>`
- **Description**: Generates an image from the given prompt.

### aiimage
- **Usage**: `!aiimage <prompt>`
- **Description**: Generates an image prompt using AI, then creates an image from it.

### weather
- **Usage** `!weather <location>`
- **Description**: Gets current weather information for a given location.

### help
- **Usage**: `!help <command>`
- **Description**: Sends usage information for the specified command.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

