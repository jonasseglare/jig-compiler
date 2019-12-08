(ns jig-compiler.binpack)

(defn pack-bin [bin-size weight-fn sorted-data]
  (let [[f & data] sorted-data]
    (loop [data data
           left [f]
           right []
           total-weight (weight-fn f)]
      (if (or (< bin-size total-weight) (empty? data))
        [left (into right data)]
        (let [[f & data] data
              next-total (+ total-weight (weight-fn f))]
          (if (<= next-total bin-size)
            (recur data (conj left f) right next-total)
            (recur data left (conj right f) total-weight)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Interface
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn pack-bins [bin-size weight-fn data]
  {:pre [(number? bin-size)
         (sequential? data)]}
  (loop [bins []
         data (sort-by (comp - weight-fn) data)]
    (if (empty? data)
      bins
      (let [[bin data] (pack-bin bin-size weight-fn data)]
        (recur (conj bins bin) data)))))

(comment

  (pack-bins identity (range 30))


  )

