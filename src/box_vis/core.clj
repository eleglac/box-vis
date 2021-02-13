(ns box-vis.core
  (:require [box-vis.render :refer :all]
            [box-vis.update :refer :all]
            [com.rpl.specter :refer :all]
            [quil.core :as q :include-macros true]
            [quil.middleware :as m]
            ))

(defn setup 
  "Establishes environmental parameters and initial state map"
  ;; NB: if (setup) takes too long,
  ;; JOGL might assume that the thread is blocked and throw an exception.
  ;; I suppose I could just catch the exception, but it's not usually an issue.
  ;; ref: https://github.com/processing/processing/issues/4468

  []

  (q/color-mode :hsb 100)
  (q/frame-rate 30) 
  (q/background 100)
  (q/perspective)

  {:camera-speed 10
   :camera-location [10 10 10] :camera-focus [0 0 0] :camera-axis [0 0 -1]
   :x-labels nil :y-labels nil :z-labels nil
   :scale [50 50 50]
   :origin [0 0 0]

   :ticker "F"

   :exp-promise nil :exps nil

   :chain-promises nil :chains nil

   :put-mesh nil :call-mesh nil

   } )

(defn update-state
  "Parent function for all state update subroutines."
  [state]
  ;; god i love the threading operators - previously i only had eyes for ->>
  ;; but on this project i learned to love ->
  (-> state
      (update-exps-state)
      (update-chain-state)
      (update-camera-state)
      ))

(defn draw-state
  "Render the visual."
  [{:keys [camera-location camera-focus camera-axis] :as state}]

  (q/background 100)

  (apply q/camera (flatten [camera-location camera-focus camera-axis])) 
  
  ; probably should render some boundary planes for the visbox
  ; TODO dynamic box size depending on actual data
  (q/stroke 33 100 50 100)
  (q/stroke-weight 2)
  (q/fill 0 100 100 100)
 
  (render-bounding-box state)
  (if (state :put-mesh) (render-mesh state {:mesh-type :put-mesh :render-color [0 100 100 10]})) ;; defaults to :put-mesh
  (if (state :call-mesh) (render-mesh state {:mesh-type :call-mesh :render-color [33 100 100 10]}))
  (render-labels state)
  )

(def sketch-settings 
  [:title       "A 3D Box Plot"
   :setup       setup
   :update      update-state
   :draw        draw-state
   :size        [1024 768]
   :renderer    :p3d
   :features    [:keep-on-top]
   :middleware  [m/fun-mode]
   ]
  )
