# Apache protocol mediator
A protocol mediator that takes in HTTP or FTP request, sends a DNS request to find the quickest IP address for the target server, and performs a protocol mediation to the target server, functioning as a proxy server.


Class project for CS656 (Internet & Higher-Layer Protocols).

- As a project for the IHLP course, HTTP-related Java APIs (ie. HttpURLConnection) were intentionally restricted in order to work with packets at the byte level for enhanced understanding of network data transmission and socket programming.

- Use of strings was prohibited, except in print statements and in DNS request, to parse and work with byte streams directly.

Q: Why is String restricted? Code will be so much faster and cleaner with String.

A: Because data on the wire is a sequence of bytes. The pedagogical point of the project rests on the fundamental fact: byte stream data is delivered in packets. Apache should process bytes (thereby letting you appreciate byte stream data) and send bytes to the client. Processing String is actually slower than processing bytes!
