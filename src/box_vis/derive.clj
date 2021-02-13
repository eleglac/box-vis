(ns box-vis.derive
  (:require [com.rpl.specter :refer :all]
            ))

;; options chain structure from options.clj/parse-chain (mapped over all expirations) is:
;; top-level: list of vecs, each vec is one expiration date's worth of calls
;; 2nd-level: vec of maps, each map is one actual option
;;
;; at 2nd-level, each vec can have different number of maps!
;; means that some options just don't exist/aren't traded for given exp dates
;; mesh has to account for that... some kind of default-value function to "fill in"

(defn where 
  "A convenience function for use with specter/select.  Returns a function that evaluates to true if given a map with a particular key val pair, false otherwise.  This might be duplicating something specter already has, but I haven't looked yet."
  ;; TODO: see if specter already does this somehow
  [k v] 
  
  #(= (k %) v))

(defn sort-ch 
  "Given an option type and a single 'row' (exp date) of option chain data, return a list of just that option type sorted by ascending strike price."
  
  [t ch]
  
  (sort 
    #(compare (:strike %1) (:strike %2)) 
    (select [ALL (where :option_type t) (submap [:symbol :expiration_date :strike :prevclose])] ch))) 

(defn full-row 
  "Prepares a mesh 'row' with an entry for every possible strike price.  This ensures that every 'row' has the same size for rendering purposes, while also maintaining sortedness."
  ;;TODO take into account other z-keys (currently hardcodes :prevclose - bad)
  [stks t ch]

  (loop [row [] s stks v (sort-ch t ch)]
    (cond
      (empty? s) ;; if there are no more strikes left to account for, return the full-row 
        row
      (= (first s) (:strike (first v))) ;; if this chain has trade data for this strike price, add it to the row
        (recur (conj row (assoc (first v) :prevclose (or (:prevclose (first v)) 0.0))) (rest s) (rest v))
      :else ;; otherwise, add a default "not found" value to the row
        (recur (conj row {:symbol :not-traded :strike (first s) :prevclose 0.0}) (rest s) v))))

(defn zmax 
  "Finds a nice round ceiling value to set z-axis scaling of visualizer."
  ;; the rest of this code I wrote sober - but this after three beers
  [max-trade]
  
  (loop [idx 0]
    (let [scale (Math/pow 10 idx)]

      (cond
        (< max-trade (* 1.0 scale)) (* 1.0 scale) 
        (< max-trade (* 2.5 scale)) (* 2.5 scale)
        (< max-trade (* 5.0 scale)) (* 5.0 scale) 
        (< max-trade (* 7.5 scale)) (* 7.5 scale) 
        :else (recur (inc idx))))))

(defn z-labels 
  "Utility function to help with display of labels for z-axis."
  [zmax]

  (map #(str (* zmax %)) [0/5 1/5 2/5 3/5 4/5 5/5]))

(defn derive-mesh
  "Given a full set of options chain data from options/get-full-chain, prepare a map to be merged into the state map for the parent visualization sketch.  This map contains the two sets of visualizer meshes (call-mesh and put-mesh) as well as the axis labels and scale metadata."

  [chain & opts] ;; chain is assumed to ALWAYS refer to the output of (map parse-chain cps)
  ;; the mesh so derived will need to be scaled to a bounding-box
  ;; may be possible to use specter to get all unique strikes w/o (distinct) TODO
  ;; TODO opts: z-key (which key to use for mesh height, default :prevclose)

  (let [;; exps = expiration dates, strings (will become y-axis labels)
        ;; stks = strike prices, floats (will become x-axis labels)
        ;; max-trade = highest price that any option traded for (will define z-scale and z-labels)
        exps (select [ALL FIRST :expiration_date] chain) ;; will be ordered as 'chain' is ordered
        stks (sort (distinct (select [ALL ALL :strike] chain)))
        max-trade (apply max (select [ALL ALL :prevclose identity] chain))

        ;; calls/puts have (count exps) rows and (count stks) columns;
        ;; (-> calls (nth row) (nth column) :prevclose) gets the (raw) z-height at (column, row) 
        calls (map (partial full-row stks "call") chain)
        puts (map (partial full-row stks "put") chain)]

    {:exps nil :exp-promise nil :chain-promises nil ;; reset once meshes are created
     :chain chain 
     :call-mesh calls
     :put-mesh  puts
     :zmax      (zmax max-trade)
     :dimensions [(count stks) (count exps) 6]
     :x-labels  (map str stks)
     :y-labels  exps
     :z-labels  (z-labels (zmax max-trade))} ))


(defn derive-bounding-box 
  "Given put-mesh, an [x y z] origin coordinate (us. [0 0 0]), and the [dx dy dz] spacing between points in each of the three dimensions, give the 3D coordinates of various relevant points."
  [{:keys [put-mesh origin scale] :as state} & opts]
  ;; TODO refangle for y-axis readjustment
  (let [[ox oy oz] origin
        [dx dy dz] scale

        x-dim (count (first put-mesh))
        y-dim (count put-mesh)
        z-dim 6 ;; TODO no magic numbers, plz

        x-max (+ ox (* dx x-dim)) 
        y-max (+ oy (* dy y-dim)) 
        z-max (+ oz (* dz z-dim))

        x-axis (conj origin x-max oy oz)
        y-axis (conj origin ox y-max oz)
        z-axis (conj origin ox oy z-max)

        bottom-rect [origin [x-max oy oz] [x-max y-max oz] [ox y-max oz]]
        back-rect   [origin [ox oy z-max] [x-max oy z-max] [x-max oy oz]]
        side-rect   [origin [ox y-max oz] [ox y-max z-max] [ox oy z-max]]]

    (merge state {:x-dim x-dim
                  :y-dim y-dim
                  :z-dim z-dim

                  :x-max x-max
                  :y-max y-max
                  :z-max z-max

                  :rects [bottom-rect back-rect side-rect]})))


(defn derive-camera
  "Given a bounding box, determine appropriate camera defaults"
  ;; opts: change eye relationship?
  [{:keys [x-max y-max z-max origin] :as state} & opts]

  (let [[ox oy oz] origin]
    
    (merge state {:camera-location [(+ (- x-max ox) z-max) (+ (- y-max oy) z-max) (+ (- z-max oz) z-max)]
                  :camera-focus    [(* (- x-max ox) 1.00) (* (- y-max oy) 0.50) 0]
                  :camera-axis     [0 0  -1]}  ;define axis directions 
           ;;TODO: change axis settings to make y-axis nicer 
           )))

