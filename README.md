# DrFockIt - the #java.de IRC bot

This is the source code of DrFockIt, a [pircbotx](https://github.com/pircbotx/pircbotx)-based IRC bot which lingers in
#java.de on the [libera](https://libera.chat) IRC network. 

## Available Commands

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
- **Description**: Gets the latest Bitcoin price in USD.

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
