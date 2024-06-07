# DrFockIt - the #java.de IRC bot

This is the source code of DrFockIt, a [pircbotx](https://github.com/pircbotx/pircbotx)-based IRC bot which 
lingers in  `#java.de` on the [libera](https://libera.chat) IRC network. DrFockIt is the successor of DrRockIt, 
a former perl-based bot; some of the old bot's data and functions were incorporated into the new bot. 

Besides providing useful services to the users of this and other channels, this project serves as a test 
bed for trying out technologies and APIs.

The bot is implemented as [spring boot](https://spring.io/projects/spring-boot) application using Java 21
and [lombok](https://projectlombok.org/), and deployed on a vps as [docker](https://www.docker.com/) container.

## Features

- The bot will respond to messages if directly addressed by its nick, using the OpenAI chat completion 
  API and the `gpt-4o` model. The bot keeps a context of 20 messages during conversation. A system prompt
  is loaded from a configuration file, giving the bot its unique personality. Note: only messages directly
  addressed to the bot, as well as its own responses to those, are added to the context and thus sent to 
  OpenAI.
- When URLs are posted to the channel, the bot will try to look them up and post a title and possibly a 
  preview of the content. Apart from plain HTML parsing, there are some special handlers for certain URLs 
  such as YouTube URLs (this one uses the YouTube API to retrieve information on the video).
- If the channel is active, the bot will occasionally post encouraging comments to the channel that have
  been added by users using the `!addslogan` command.
- The bot keeps track of each user's karma, which can be increased or decreased using `++nick` and `--nick` 
  respectively.
- The bot keeps track of when users where last seen on the channel (meaning when they last said something, 
  and what they said).
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

### karma
- **Usage**: `!karma <nick or thing>`
- **Description**: Shows the recorded karma for the specified nick or thing.

### lagerfeld
- **Usage**: `!lagerfeld <text>`
- **Description**: Responds with an AI-generated Karl Lagerfeld quote.

### aireset
- **Usage**: `!aireset`
- **Description**: Deletes the current context for the channel and reloads the system prompt.

### quote
- **Usage**: `!quote <nick>`
- **Description**: Records a quote from the specified nick.

### quoter
- **Usage**: `!quoter <nick>`
- **Description**: Retrieves a random quote from the specified nick.

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

### fx
- **Usage**: `!fx <symbols>`
- **Description**: Gets currency exchange rates.

### thaigold
- **Usage**: `!thaigold`
- **Description**: Gets current Thai gold price information.

### help
- **Usage**: `!help <command>`
- **Description**: Sends usage information for the specified command.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [pircbotx](https://github.com/pircbotx/pircbotx) - The IRC library used in this project.
- The #java.de community for their support and ideas.
