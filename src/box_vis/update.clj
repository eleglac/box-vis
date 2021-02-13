(ns box-vis.update
  (:require [box-vis.derive :refer :all]
            [box-vis.options :refer :all]
            
            [com.rpl.specter :refer :all] 
            
            [quil.core :as q :include-macros true]
            ))


(defn update-exps-state
  "Update state relating to option expirations data"
  [{:keys [exp-promise exps ticker chain] :as state}]

  (cond
    chain
      state

    (nil? exp-promise)
      (assoc state :exp-promise (get-expirations-promise ticker))
      
    (and (realized? exp-promise) (or (nil? exps) (not= exps (parse-expirations exp-promise))))
      (assoc state :exps (parse-expirations exp-promise))
      
    :else
      state
   )) 

(defn update-chain-state
  "Update state relating to option chain data"
  [{:keys [chain-promises chain exps ticker] :as state}]

  (cond
    (and exps (nil? chain) (nil? chain-promises))
      (assoc state :chain-promises (map (partial get-chain-promise ticker) exps))
        
    (and exps (nil? chain) (apply (every-pred true?) (map realized? chain-promises)))
      (let [chs (sort
                  ;; this compare reverse-orders the exp dates, change when we redo y-axis 
                  #(compare (select [FIRST :expiration_date] %2) (select [FIRST :expiration_date] %1)) 
                  (map parse-chain chain-promises))
            mesh-and-labels (derive-mesh chs)]
        ;; order matters for the following: derive-camera depends on derive-bounding-box, etc.
        
        (-> (merge state mesh-and-labels)
            (derive-bounding-box)
            (derive-camera)))
    
    ;; TODO need to handle case where new ticker is requested... probably different update function

    :else
      state
   ))

(defn update-camera-state
  [{:keys [camera-location camera-focus camera-speed] :as state}]

  (let [[lx ly lz] camera-location
        [fx fy fz] camera-focus
        kp (q/key-as-keyword)
        ]
  ;; currently kp does not get un-set when the key is no longer being pressed;
  ;; fine for now but violates 'least surprise' principle IMO
  ;; effect is that camera/focus will continue to "pan" until a non-relevant
  ;; key is pressed (i usually use space)
    (cond
      (= :a     kp) (assoc state :camera-location [(- lx camera-speed) ly lz] :camera-focus [(- fx camera-speed) fy fz])
      (= :d     kp) (assoc state :camera-location [(+ lx camera-speed) ly lz] :camera-focus [(+ fx camera-speed) fy fz])
      (= :w     kp) (assoc state :camera-location [lx (- ly camera-speed) lz] :camera-focus [fx (- fy camera-speed) fz]) 
      (= :s     kp) (assoc state :camera-location [lx (+ ly camera-speed) lz] :camera-focus [fx (+ fy camera-speed) fz])
      (= :e     kp) (assoc state :camera-location [lx ly (- lz camera-speed)] :camera-focus [fx fy (- fz camera-speed)])
      (= :q     kp) (assoc state :camera-location [lx ly (+ lz camera-speed)] :camera-focus [fx fy (+ fz camera-speed)])
      (= :left  kp) (assoc state :camera-focus [(- fx camera-speed) fy fz])
      (= :right kp) (assoc state :camera-focus [(+ fx camera-speed) fy fz])
      (= :up    kp) (assoc state :camera-focus [fx (- fy camera-speed) fz]) 
      (= :down  kp) (assoc state :camera-focus [fx (+ fy camera-speed) fz])
      :else state
      
      )))
