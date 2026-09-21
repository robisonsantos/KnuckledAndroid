# KnuckleGame

A game based on the Cult of the Lamb's "Knucklebones".
It's a two players game, where players tries to fill in a 3x3 grid with dice rolls, in turns, until one player can fill their whole grid.
The player with the higher score wins.

## Architecture

The game will follow a server/client architecture over bluetooth.
One player initiates a session, while the other connects to it using bluetooth. The host generates a random PIN so the client can connect to it.
All the game state is present on the server "player" only. The logic to randomize the dice roll, store the score, grid state and decide who wins all remain on the server. The client keeps a copy of the game state in memory. It receives the updated game state from the server after each play, and renders the diff. Game state includes, at least:

- whose turn is it
- players score
- grids state
- who is currently playing

The game state is reset for each new session and lives in memory only.

## UX

- The game should allow a player to select their name prior to initiate the session
- The game should show a "rolling die" animation while one or another player is playing 
- The screen shows both player's grids and a die roll area
- A player rolls the dice by touching on it
- The die "rolls" for about 2 seconds
- The game should show the current score for each player
- The game should indicate who's turn is now
- The player drags the die to the column of their grid
- After the die is placed on the grid, a new die appers on the "roll" area
- A player is not able to add a die to a complete column
- Once the game ends, the game show a win animation to the player that won
- Once the game ends, the game show a loose animation to the player that lost
- Once the game ends, the game shows the option to disconnect or play again
- To play again, the game state resets, but player names remain

## Game rules

The game consists of two 3x3 boards, each belonging to their respective player.
The players take turns. On a player's turn, they roll a single 6-sided die, and must place it in a column on their board. A filled column does not accept any more dice.
Each player has a score, which is the sum of all the dice values on their board. The score awarded by each column is also displayed.
If a player places multiple dice of the same value in the same column, the score awarded for each of those dice is multiplied by the number of dice of the same value in that column. e.g. if a column contains 4-1-4, then the score for that column is (4+4)x2 + 1 = 17. Below is a multiplication table for reference and comparison:

|Die value | 1 die in the column | 2 dice	| 3 dice |
|----------|---------------------|--------|--------|
| 1	| 1 |	4 |	9 |
| 2	| 2 | 8 | 18 |
| 3	| 3	| 12 | 27 |
| 4	| 4	| 16 | 36 |
| 5	| 5	| 20 | 45 |
| 6	| 6	| 24 | 54 |

When a player places a dice, all dice of the same value in the corresponding column of the opponent's board gets destroyed. Players can use this mechanic to destroy their opponent's high-scoring combos.
The game ends when either player completely fills up their 3x3 board. The player with the higher score wins.

## References

There is a small dice roll game implemented at $HOME/AndroidStudioProjects/DiceGame. It already implements the client/server architecture, it has the logic to roll a die, sound effects and the 3D model for the die itself. We will use that implementation to copy logic and resources as needed.

Here is a youtube video of the original game being played: https://www.youtube.com/watch?v=y4PfvZiEs5E

## Tests

- We have unit tests to guide implementation 
- We use maestro and maestro MCP to help with integration tests
- We run manual tests once all the other tests are complete
