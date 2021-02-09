(ns box-vis.core
  (:require [box-vis.options :refer :all]
            [quil.core :as q :include-macros true]
            [quil.middleware :as m]
            [com.rpl.specter :refer :all]
            ))
;; TODO learn shaders for transparent meshes?! eugh
(defn normalize 
  "Given an array of values, scale them to be proportional to a maximum."
  ;; WILL NOT WORK WITH LIST OF LISTS!
  [vs max-v]

  (let [mv (max vs)
        sf (/ max-v mv)]
    (map (partial * sf) vs)))

(defn find-range 
  "Given a key and a set of maps, find the [min max] 
  for the values of that key in those maps"
  [k ms]
  (let [vs (select [ALL k] ms)] [(apply min vs) (apply max vs)]))

(defn create-mesh [x-dim y-dim dx dy & zs] 
  "creates a map representing a heightmap mesh with size metadata.
   1. x-dim and y-dim are the number of rows and columns; z will be height
   2. dx and dy represent spacing between coordinates in each direction
   3. zs represents a y*x matrix (y rows, x cols) of z-heights.
      if not provided, a constant value of 100 will be used."
  
    {:x-dim x-dim :dx dx
     :y-dim y-dim :dy dy
     :z-range (if zs
                (let [nzs (normalize zs 300)] [(min nzs) (max nzs)]) ; redundant...?
                [0 300]) 
     :mesh (for [y (range y-dim)
                 x (range x-dim)]
             (if zs
               [(* x dx) (* y dy) (-> zs (nth y) (nth x))]
               [(* x dx) (* y dy) 100]))
     })
        
(defn find-range-ceiling 
  "Utility function to help with display of labels for z-axis."
  ;; the rest of this code I wrote sober - but this after three beers
  [n]
  
  (loop [idx 0]
    (let [scale (Math/pow 10 idx)]
      (cond
        (< n (* 1.0 scale)) (* 1.0 scale)
        (< n (* 2.5 scale)) (* 2.5 scale)
        (< n (* 5.0 scale)) (* 5.0 scale)
        (< n (* 7.5 scale)) (* 7.5 scale)
        :else (recur (inc idx))))))

(defn find-z-labels 
  "Utility function to produce axis labels given a max trade price.
  Uses find-range-ceiling."
  ;; this function took another 0.5 beers atop find-range-ceiling
  [max-trade]

  (let [ceiling (find-range-ceiling max-trade)]
    (map (fn [n] (str (* ceiling n))) [0/5 1/5 2/5 3/5 4/5 5/5])))

(defn find-labels 
  "Given put/call-mesh-data, find the axis labels (x y z)"
  [mesh-data]
  {:x-labels (map str (sort (distinct (select [ALL :strike] mesh-data))))
   :y-labels (distinct (select [ALL :expiration_date] mesh-data))
   :z-labels (map str (find-z-labels (nth (find-range :close mesh-data) 1)))
   }
  
  )

(defn find-bounding-box 
  "Given a mesh1d, determine an appropriate bounding box size."
  ;; opts: change origin
  [{:keys [x-dim y-dim dx dy z-range] :as mesh1d} & opts]

  (let [origin [0 0 0]

        x-max (* dx x-dim )
        y-max (* dy y-dim )
        z-max (nth z-range 1)

        x-axis (conj origin x-max 0 0) ; TODO reflect change of origin
        y-axis (conj origin 0 y-max 0) ; if applicable
        z-axis (conj origin 0 0 z-max)

        bottom-rect [origin [x-max 0 0] [x-max y-max 0] [0 y-max 0]] ; here too
        back-rect   [origin [0 0 z-max] [x-max 0 z-max] [x-max 0 0]]
        side-rect   [origin [0 y-max 0] [0 y-max z-max] [0 0 z-max]]
        ]

    {:origin origin

     :x-max x-max
     :y-max y-max
     :z-max z-max

     :rects [bottom-rect back-rect side-rect] 
     }))

(defn find-camera
  "Given a bounding box, determine appropriate camera defaults"
  ;; opts: change eye relationship?
  [{:keys [x-max y-max z-max] :as bbox} & opts]

  { 
   :camera-location [(* x-max 1.50) (* y-max 2.00) (* z-max 2.00)] ;camera "eye" position, x y z
   :camera-focus    [(* x-max 0.50) (* y-max 0.50) (* z-max 0.50)] ;point at which the camera is looking, x y z
   :camera-axis     [000 000  -1]  ;define axis directions 
  })

(defn render-3d-polygon
  "Convenience function to render a set of verts as a polygon."
  [verts]

  (do
    (q/begin-shape)
    (doseq [v verts] (apply q/vertex v))
    (q/end-shape :close)))

(defn render-bounding-box
  "Subroutine to render a bounding box.  Use with find-bounding box, eg.
  (-> mesh1d (find-bounding-box) (render-bounding-box))"

  [{:keys [rects] :as bbox} & opts]
  ;; opts: render colors, etc.
  (let [fill-color   [33 100 0  20]
        stroke-color [66 100 0 100]
        stroke-weight 3] ; thick lines 
    (do
      (apply q/fill fill-color)
      (apply q/stroke stroke-color)
      (q/stroke-weight stroke-weight)
      (doseq [p rects] (render-3d-polygon p))
      (q/stroke-weight 1))))

(defn render-mesh [mesh1d & opts]
  "Render a mesh created by create-mesh by traversing a 1d mesh array 
  in a particular way to create a nice triangle-strip-based surface."
  ;; TODO: use opts to set rendering colors

  (let [x-dim (mesh1d :x-dim)
        y-dim (mesh1d :y-dim)
        padding (/ (mesh1d :dx) 2)
        mesh2d (partition x-dim (mesh1d :mesh))]
    (do
      ;; shift the mesh over a bit
      (q/push-matrix)
      (q/translate padding padding padding)
      
      ;; render the actual vertices as colored dots
      (q/stroke 0 100 100 100)
      (q/stroke-weight 3)
      (doseq [v (mesh1d :mesh)] (apply q/point v))
      (q/stroke-weight 1)

      ;; prepare to render the triangle-strip
      (q/no-stroke)
      (q/fill 0 100 100 10)
      (q/begin-shape :triangle-strip)
      (loop [x-offset 0 y-offset 0] ;; begin rendering strip
        (cond
          (= y-offset (dec y-dim)) ;; if we've reached the last row of verts
          (do 
            (q/end-shape) 
            (q/pop-matrix))           ;; we're done 

          (= x-offset (dec x-dim)) ;; if we've reached the last column of verts
          (do                      ;; finish up the last triangle and reset for the next row
            (apply q/vertex (-> mesh2d (nth      y-offset)  (nth x-offset)))
            (apply q/vertex (-> mesh2d (nth (inc y-offset)) (nth x-offset)))
            (q/end-shape) ;; avoid extra triangle connecting end of one strip
            (q/begin-shape :triangle-strip) ;; to beginning of next strip
            (recur
              0
              (inc y-offset)))

          :else ;; if we're neither at the end of the columns or rows,
          (do   ;; render a set of verts and move on to the next set
            (apply q/vertex (-> mesh2d (nth      y-offset)  (nth      x-offset)))
            (apply q/vertex (-> mesh2d (nth (inc y-offset)) (nth      x-offset)))
            (apply q/vertex (-> mesh2d (nth (inc y-offset)) (nth (inc x-offset))))
            (apply q/vertex (-> mesh2d (nth      y-offset)  (nth      x-offset)))
            (recur
              (inc x-offset)
              y-offset)))
        ))))

(defn render-labels
  "Wrapper function for graph label display."
  [x-labels y-labels z-labels spacing & opts]

  (let [
        x-max (* spacing (count x-labels)) 
        y-max (* spacing (count y-labels))
        z-max (* spacing (count z-labels))

        padding (/ spacing 2)
        
        rt2o2 (/ (Math/sqrt 2) 2)
        sc (partial * rt2o2) ;;compensate for 45 degree angles
        ]
    (do (q/with-fill [0 100 0 100]
      (q/stroke 0 100 0 100)
      (q/text-size 16)
      ;; other text setup functions here... (font?)

      ;; print x labels
      (q/push-matrix)
      (q/text-align :right :top)
      (q/rotate-z (- q/HALF-PI))
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (sc padding) (sc padding))
      (doseq [idx (range (count x-labels))]
        (q/line (- (- y-max padding)) (sc spacing idx) (sc spacing idx) (- (+ 40 y-max)) (sc spacing idx) (sc spacing idx))
        (q/text (nth x-labels idx) (- (+ 20 y-max)) (sc spacing idx) (sc spacing idx)))
      (q/pop-matrix)

      ;; print y labels
      (q/push-matrix)
      (q/text-align :left :top)
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (sc padding) (sc padding))
      (doseq [idx (range (count y-labels))]
        (q/line (- x-max padding) (sc spacing idx) (sc spacing idx) (+ 40 x-max) (sc spacing idx) (sc spacing idx))
        (q/text (nth y-labels (- (dec (count y-labels)) idx)) (+ 20 x-max) (sc spacing idx) (sc spacing idx)))
      (q/pop-matrix)
      
      ;; print z labels
      (q/push-matrix)
      (q/text-align :left :bottom)
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (- (sc padding)) (sc padding))
      (doseq [idx (range (count z-labels))]
        (q/line (- x-max padding) (- (sc spacing idx)) (sc spacing idx) (+ 40 x-max) (- (sc spacing idx)) (sc spacing idx))
        (q/text (nth z-labels idx) (+ 20 x-max) (- (sc spacing idx)) (sc spacing idx)))
      (q/pop-matrix))

      )))

(defn setup 
  "Establishes environmental parameters and initial state."
  ;; NB: if (setup) takes too long,
  ;; JOGL might assume that the thread is blocked and throw an exception.
  ;; I suppose I could just catch the exception, but it's not usually an issue.
  ;; ref: https://github.com/processing/processing/issues/4468

  []

  (q/color-mode :hsb 100)
  (q/frame-rate 30) 
  (q/background 100)
  ;(q/ortho)
  (q/perspective)

  (let [default-mesh (create-mesh 12 6 50 50)
        default-bbox (find-bounding-box default-mesh)
        default-camera (find-camera default-bbox)
        ]
    ;; create state map for middleware/fun-mode
    (merge default-camera
           {:default-mesh default-mesh 
            :bound-box default-bbox
                       
            :x-labels ["4.00" "5.00" "6.00" "7.00" "8.00" "9.00" 
                       "10.00" "11.00" "12.00" "13.00" "14.00" "15.00"]
            :y-labels ["2021-02-05" "2021-02-12" "2021-02-19" "2021-02-26" "2021-03-05" "2021-03-12"]
            :z-labels ["0.00" "5.00" "10.00" "15.00" "20.00"]
            :spacing 50
           
            :ticker "ATAX"

            :exp-promise nil
            :exps nil

            :chain-promises nil
            :chains nil

            :put-chains nil
            :call-chains nil

            :put-mesh-data nil
            :call-mesh-data nil

            })))

(defn update-exps-state
  "Update state relating to option expirations data"
  [{:keys [exp-promise exps ticker] :as state}]

  (cond
    (nil? exp-promise)
      (assoc state :exp-promise (get-expirations-promise ticker))
      
    (and (realized? exp-promise) (or (nil? exps) (not= exps (parse-expirations exp-promise))))
      (assoc state :exps (parse-expirations exp-promise)) 
      
    :else
      state
   )) 

(defn update-chain-state
  "Update state relating to option chain data"
  [{:keys [chain-promises chains exps ticker] :as state}]

  (cond
    (and exps (nil? chain-promises) (nil? chains))
      (assoc state :chain-promises (map (partial get-chain-promise ticker) exps))
        
    (and exps (nil? chains) (apply (every-pred true?) (map realized? chain-promises)))
      (let [chs (map parse-chain chain-promises)
            p-chs (select [ALL ALL #(= (:option_type %) "put")] chs)
            c-chs (select [ALL ALL #(= (:option_type %) "call")] chs)
            raw-put-mesh-data (select [ALL (submap [:close :strike :expiration_date :symbol])] p-chs)
            raw-call-mesh-data (select [ALL (submap [:close :strike :expiration_date :symbol])] c-chs)
            pmd (transform [ALL :close nil?] (fn [n] 0.0) raw-put-mesh-data)
            cmd (transform [ALL :close nil?] (fn [n] 0.0) raw-call-mesh-data)]
        (assoc state :chains chs
                     :put-chains p-chs
                     :call-chains c-chs
                     :put-mesh-data pmd
                     :call-mesh-data cmd)) 
    
    ;; TODO need to handle case where new ticker is requested... probably different update function

    :else
      state
   ))

(defn update-labels
  "Update the labels for the display, once chains are loaded"
  [{:keys [chains put-mesh-data] :as state}]

  (cond
    (nil? chains)
      state
    (not (nil? put-mesh-data))
      (merge state (find-labels put-mesh-data))
    :else
      state
    )
  )

;(defn create-mesh [x-dim y-dim dx dy & zs] 
  ;"creates a map representing a heightmap mesh with size metadata.
   ;1. x-dim and y-dim are the number of rows and columns; z will be height
   ;2. dx and dy represent spacing between coordinates in each direction
   ;3. zs represents a y*x matrix (y rows, x cols) of z-heights.
      ;if not provided, a constant value of 100 will be used."
  
    ;{:x-dim x-dim :dx dx
     ;:y-dim y-dim :dy dy
     ;:z-range (if zs
                ;(let [nzs (normalize zs 300)] [(min nzs) (max nzs)]) ; redundant...?
                ;[0 300]) 
     ;:mesh (for [y (range y-dim)
                 ;x (range x-dim)]
             ;(if zs
               ;[(* x dx) (* y dy) (-> zs (nth y) (nth x))]
               ;[(* x dx) (* y dy) 100]))
     ;})

;; TODO finish mesh creator

(defn update-mesh-state
  [{:keys [put-mesh-data call-mesh-data spacing] :as state}]
  
  (let [x-dim (-> put-mesh-data (select [ALL :strike]) (distinct) (count))
        y-dim (-> put-mesh-data (select [ALL :expiration_date]) (distinct) (count))
        ])
  state ;; passthru until working function!
  )

(defn update-camera-state
  [{:keys [camera-location camera-focus] :as state}]

  (let [[lx ly lz] camera-location
        [fx fy fz] camera-focus
        kp (q/key-as-keyword)
        speed 5
        ]
  ;; currently kp does not get un-set when the key is no longer being pressed;
  ;; fine for now but violates 'least surprise' principle IMO
  ;; effect is that camera/focus will continue to "pan" until a non-relevant
  ;; key is pressed (i usually use space)
    (cond
      (= :a     kp) (assoc state :camera-location [(- lx speed) ly lz])
      (= :d     kp) (assoc state :camera-location [(+ lx speed) ly lz])
      (= :w     kp) (assoc state :camera-location [lx (- ly speed) lz]) 
      (= :s     kp) (assoc state :camera-location [lx (+ ly speed) lz])
      (= :e     kp) (assoc state :camera-location [lx ly (- lz speed)])
      (= :q     kp) (assoc state :camera-location [lx ly (+ lz speed)])
      (= :left  kp) (assoc state :camera-focus [(- fx speed) fy fz])
      (= :right kp) (assoc state :camera-focus [(+ fx speed) fy fz])
      (= :up    kp) (assoc state :camera-focus [fx (- fy speed) fz]) 
      (= :down  kp) (assoc state :camera-focus [fx (+ fy speed) fz])
      :else state

      ) )

  )

(defn update-bbox-state 
  [{:keys [x-labels y-labels z-labels spacing] :as state}]
  (let [new-bbox (find-bounding-box (create-mesh (count x-labels) (count y-labels) spacing spacing))
        ]
    (assoc state :bound-box new-bbox))
  )

(defn update-state
  "Parent function for all state update subroutines."
  [state]
  ;; god i love the threading operators - previously i only had eyes for ->>
  ;; but on this project i learned to love ->
  (-> state
      (update-exps-state)
      (update-chain-state)
      (update-labels)
      (update-mesh-state)
      (update-bbox-state)
      (update-camera-state)
      ))

(defn draw-state
  "Render the visual."
  [{:keys [camera-location camera-focus camera-axis 
           default-mesh bound-box
           mouse-dx mouse-dy
           x-labels y-labels z-labels spacing
           ] :as state}]

  (q/background 100)

  (apply q/camera (flatten [camera-location camera-focus camera-axis])) 
  
  ; probably should render some boundary planes for the visbox
  ; TODO dynamic box size depending on actual data
  (q/stroke 33 100 50 100)
  (q/stroke-weight 2)
  (q/fill 0 100 100 100)
  
  (render-bounding-box bound-box) 
  (render-mesh default-mesh)
  (render-labels x-labels y-labels z-labels spacing)
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
