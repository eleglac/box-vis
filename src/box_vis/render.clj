(ns box-vis.render
  (:require [com.rpl.specter :refer :all]
            
            [quil.core :as q :include-macros true]
            ))

(defn render-3d-polygon
  "Convenience function to render a set of verts as a polygon."
  [verts]

  (do
    (q/begin-shape)
    (doseq [v verts] (apply q/vertex v))
    (q/end-shape :close)))

(defn render-bounding-box
  "Subroutine to render a bounding box stored in (state :rects)."
  ;; opts: render colors, etc.
  [{:keys [rects] :as state} & opts]
  
  (let [fill-color   [33 100 0  20]
        stroke-color [66 100 0 100]
        stroke-weight 3] ; thick lines 
    (do
      (apply q/fill fill-color)
      (apply q/stroke stroke-color)
      (q/stroke-weight stroke-weight)
      (doseq [p rects] (render-3d-polygon p))
      (q/stroke-weight 1))))

(defn render-mesh
  "Render a mesh created by create-mesh by traversing a 1d mesh array 
  in a particular way to create a nice triangle-strip-based surface."
  ;; TODO: opts: surface?, dots?, z-key?
  ([state] (render-mesh state {:mesh-type :put-mesh :render-color [0 100 100 10]}))

  ([{:keys [origin scale dimensions zmax] :as state} {:keys [mesh-type render-color] :as opts}] 
   (let [[ox oy oz] origin
         [dx dy dz] scale
         [x-dim y-dim z-dim] dimensions
         [x-pad y-pad z-pad] (map #(/ % 2) scale)
         [h s b a] render-color

         mesh (state mesh-type)

         z-key :prevclose 
         z-scale-factor (* dz (dec z-dim) (/ 1 zmax))
         ]

     (do
       ;; shift the mesh over a bit
       (q/push-matrix)
       (q/translate ox oy oz)
       (q/translate x-pad y-pad z-pad)

       ;; render the actual vertices as colored dots
       ;; TODO: add colored lines?
       (q/stroke h s b 100)
       (q/stroke-weight 3)
       (doseq [row (range y-dim) col (range x-dim)] 
         ;(let [x (* dx col) y (* dy row) z (-> mesh (nth row) (nth col) z-key (* z-scale-factor))
               ;s (-> mesh (nth row) (nth col) :symbol)]
         ;(q/text (str s) x y z)
         (q/point (* dx col) (* dy row) (-> mesh (nth row) (nth col) z-key (* z-scale-factor))));)
       (q/stroke-weight 1)

       ;; prepare to render the triangle-strip
       (q/no-stroke)
       (apply q/fill render-color)
       (q/begin-shape :triangle-strip)

       (loop [col 0 row 0] ;; begin rendering strip
         (cond
           (= row (dec y-dim)) ;; if we've reached the last row of verts, we're done
           (do (q/end-shape) (q/pop-matrix))

           (= col (dec x-dim)) ;; if we've reached the last column of verts
           (do                      ;; finish up the last triangle and reset for the next row
               (q/vertex (* dx      col)  (* dy      row)  (-> mesh (nth      row)  (nth      col) z-key (* z-scale-factor)))
               (q/vertex (* dx      col)  (* dy (inc row)) (-> mesh (nth (inc row)) (nth      col) z-key (* z-scale-factor)))
               (q/end-shape) ;; avoid extra triangle connecting end of one strip
               (q/begin-shape :triangle-strip) ;; to beginning of next strip
               (recur 0 (inc row)))

           :else ;; if we're neither at the end of the columns or rows,
           (do   ;; render a set of verts and move on to the next set
               (q/vertex (* dx      col)  (* dy      row)  (-> mesh (nth      row)  (nth      col)  z-key (* z-scale-factor)))
               (q/vertex (* dx      col)  (* dy (inc row)) (-> mesh (nth (inc row)) (nth      col)  z-key (* z-scale-factor)))
               (q/vertex (* dx (inc col)) (* dy (inc row)) (-> mesh (nth (inc row)) (nth (inc col)) z-key (* z-scale-factor)))
               (q/vertex (* dx      col)  (* dy      row)  (-> mesh (nth      row)  (nth      col)  z-key (* z-scale-factor)))
               (recur (inc col) row)))
         )))))

(defn render-labels
  "Wrapper function for graph label display."
  [{:keys [x-labels y-labels z-labels scale origin] :as state} & opts]

  (let [[dx dy dz] scale
        [ox oy oz] origin
        [x-max y-max z-max] (map * scale (map count [x-labels y-labels z-labels]))
        [x-pad y-pad z-pad] (map #(/ % 2) scale)
        
        rt2o2 (/ (Math/sqrt 2) 2)
        sc (partial * rt2o2) ;;compensate for 45 degree angles
        ]
    ;; potentially this whole function needs to be re-done
    ;; particularly to fix up the y-labels (i.e. strike dates)

    (do (q/with-fill [0 100 0 100]
      (q/stroke 0 100 0 100)
      (q/text-size 16)
      ;; other text setup functions here... (font?)

      ;; print x labels
      (q/push-matrix)
      (q/text-align :right :top)
      (q/rotate-z (- q/HALF-PI))
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (sc y-pad) (sc z-pad))
      (doseq [idx (range (count x-labels))]
        (q/line (- (- y-max y-pad)) (sc dy idx) (sc dz idx) (- (+ 40 y-max)) (sc dy idx) (sc dz idx))
        (q/text (nth x-labels idx) (- (+ 20 y-max)) (sc dy idx) (sc dz idx)))
      (q/pop-matrix)

      ;;print y labels
      (q/push-matrix)
      (q/text-align :left :top)
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (sc y-pad) (sc z-pad))
      (doseq [idx (range (count y-labels))]
        (q/line (- x-max x-pad) (sc dy idx) (sc dz idx) (+ 40 x-max) (sc dy idx) (sc dz idx))
        ;(q/text (nth y-labels (- (dec (count y-labels)) idx)) (+ 20 x-max) (sc dy idx) (sc dz idx)))
        (q/text (nth y-labels idx) (+ 20 x-max) (sc dy idx) (sc dz idx)))
      (q/pop-matrix)
      
      ;; print z labels
      (q/push-matrix)
      (q/text-align :left :bottom)
      (q/rotate-x (- q/QUARTER-PI))
      (q/translate 0 (- (sc y-pad)) (sc z-pad))
      (doseq [idx (range (count z-labels))]
        (q/line (- x-max x-pad) (- (sc dy idx)) (sc dz idx) (+ 40 x-max) (- (sc dy idx)) (sc dz idx))
        (q/text (nth z-labels idx) (+ 20 x-max) (- (sc dy idx)) (sc dz idx)))
      (q/pop-matrix))

      )))
