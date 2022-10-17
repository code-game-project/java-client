# Java-Client
![CodeGame Version](https://img.shields.io/badge/CodeGame-v0.7-orange)
![Java Version](https://img.shields.io/badge/Java-17-brown)

The Java client library for [CodeGame](https://code-game.org).

## Installation

Add this to your *pom.xml*:
```xml
<dependency>
  <groupId>org.codegame</groupId>
  <artifactId>client</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Usage

```java
// Create a new game socket.
var socket = new GameSocket("games.code-game.org/example");

// Create a new private game.
var game = socket.createGame(false, false, null);

// Join a game.
socket.join(game.id, "username");

// Spectate a game.
socket.spectate(game.id);

// Connect with an existing session.
socket.restoreSession("username");

// Register an event listener for the `my_event` event.
socket.on("my_event", MyEvent.class, (data) -> {
	// TODO: do something with `data`
});

// Send a `hello_world` command.
socket.send("hello_world", new HelloWorldCmd("Hello, World!"));

// Wait until the connection is closed.
socket.listen();
```

## License

MIT License

Copyright (c) 2022 Julian Hofmann

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
