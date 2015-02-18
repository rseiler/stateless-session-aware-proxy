# Stateless, session aware, proxy

This proxy handles the sessions to the called websites and is itself session less.
The project is born because of a specific monitoring issue:
on a monitoring website, each server is embedded as a frame and all servers are using the same domain. The monitoring site
refreshes itself every few seconds. So with each request to one server creates a new session. Because the second server
doesn't know anything about the session from the first server and so on.
