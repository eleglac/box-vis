# box-vis

A visualizer for stock options data, built with Clojure and Quil.

Currently a WIP; the eventual goal is for it to be an interactive web applet.

TODOs:
- Convert to CLJS and host online
- Increase robustness of visualizer
  - Add lines, more data, "mouse hover," control over sub-sectional views
  - Make strike date categories clearer (eg "within next month" "within next year" "later")
- User interaction file (to go with derive, render, update etc)
- Investigate conversion to Fulcro
  - Most pieces of this app are a function of a state object anyway
- More 2D views, potentially using Oz
  - Eg. "slices" of the 3D view (eg trade price vs. date for a given strike price, or vice versa)

## Usage

At the moment, it's best run from a repl:

`(apply q/sketch sketch-settings)`

## License

I haven't picked one yet, but for right now you can use this code for personal noncommercial purposes and no other.

