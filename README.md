# About

This repository aims to host some docker composition which isolate one
particular geOrchestra component in order to allow easy debugging of said
component.

The main docker composition repository could have been used for such a use
case, but:

1. it became more and more complex over time
2. the datadir can be easily out-of-sync (envsusbt introducing confusion)
3. running every services at the same time is generally not necessary
4. it assumes HTTPS, and there are some issues with the certificate provided by
   traefik.me (and developers generally don't need the TLS overhead in order to
   debug)

# Contributing

When contributing, please provide:

* a docker composition (obviously)
* some e.g. curl commands to highlight a particular bug
* Add a readme to the directory which explains the goal of the composition


