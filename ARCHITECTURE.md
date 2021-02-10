# Architecture

## High-level description

Box-Vis is primarily built with Quil, the Clojure(script) wrapper for Processing.  It is intended to produce a 3-D visualizer for stock options chain data.

Currently, I see the following as the core "pieces" of the app:
### options.clj
- Data gathering using http-kit, Specter, and Cheshire (working)

### core.clj
- Generating visualization components from options data structure (partially working)
  - Finding visualization "box" dimensions (working)
  - Finding visualization "labels" (working)
  - Finding visualization "mesh" (not working)
- Rendering visualization components (working)
- Handling user input (working)

These processes mainly come together in the "update-state" and "draw-state" functions, which are "sketch" entry points provided by Quil's "fun-mode" (presumably, "functional mode").  Thus the app is ostensibly a pure function of the options data and any user-provided settings once those are added.

## Issues/Pain Points

Currently the data flow through the app is pretty convoluted.  The core data structure is the "state" map, again provided by Quil fun-mode.  This map is completely defined by the program and is updated every frame by default.

For this app, the map of options chain data provided by options.clj is really the fundamental data structure, as everything about the visualization should be derived from it.  However, this is not done in a particularly consistent way currently: some values are derived immediately (i.e. when the options-data is first fetched), some later, some values are derived from other derived values, etc. The most immediate pain point right now is that a 3D "mesh" cannot be created directly from the options data structure.

Additionally, the user interface is currently quite weak.  While camera control has been implemented, there is no indication of how those controls work.  These controls also violate the "principle of least surprise" in that a given movement will persist even after the user is no longer triggering that control.  Additional configuration options (for example, allowing a new set of options data to be selected within the app) are yet to be established, and will likely comprise a second set of state variables needing a second family of update functions.

## Goal architecture

State map will contain three main families of variables:
- Options chain and derived values
- User configuration & interface values
- Quil environment settings, flags, etc. (may not be strictly necessary in actual usage)

Each family of variables will have associated rendering and updating functions.

The app will eventually be ported to Clojurescript and hosted at stonks.expert.

Options request data will be cached so I won't be hitting the Tradier API more than I have to.

## Decisionmaking
- Since it's intended as a data visualization, I originally considered using something like Om, but a brief investigation convinced me that there's not much out there for 3D visualizations and Quil is already somewhat familiar so that's what I went with.

- I went with http-kit over clj-http due to a more convenient handling of asynchronous responses - why on Earth does clj-http not produce Clojure data structures when async responses are returned?

- Specter is incredible and if people aren't already using it they should be

- Writing this document at all was inspired by [this blog post](https://matklad.github.io//2021/02/06/ARCHITECTURE.md.html) (even if I haven't heeded any specific advice therein) as well as consultation with [@oneforwonder](https://github.com/oneforwonder)
