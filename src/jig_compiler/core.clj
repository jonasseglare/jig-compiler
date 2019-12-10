(ns jig-compiler.core
  (:require [clojure.spec.alpha :as spec]
            [clj-http.client :as http-client]
            [hickory.core :as hickory]
            [clojure.string :as cljstr]
            [clojure.java.io :as io]
            [cemerick.url :as cemerick]
            [cheshire.core :as cheshire]
            [clojure.java.shell :as shell]
            [jig-compiler.binpack :as bp]))


(defn element-or-string? [x]
  (or (string? x)
      (and (map? x)
           (= :element (:type x)))))

(spec/def ::url string?)
(spec/def :scraper/src-address ::url)
(spec/def :scraper/dst-directory string?)
(spec/def :scraper/settings (spec/keys :req [:scraper/src-address
                                             :scraper/dst-directory]))

(defn href [x]
  (-> x :attrs :href))

(defn parse-long [x]
  (try
    (Long/valueOf (cljstr/trim x))
    (catch Exception e
      nil)))

(defn parse-double [x]
  (try
    (Double/valueOf x)
    (catch Exception e
      nil)))

(defn blank-string? [x]
  (and (string? x)
       (cljstr/blank? x)))
(defn non-blank-string? [x]
  (not (blank-string? x)))

(defn blank-string-but-single-space? [x]
  (and (blank-string? x)
       (not (= x " "))))

(defn tune-number? [x]
  (and (map? x)
       (= :font (:tag x))
       (parse-long (first (:content x)))))

(spec/def :br/tag #{:br})
(spec/def :a/tag #{:a})
(spec/def :font/tag #{:font})
(defn tune-href? [x] (let [x (cljstr/lower-case x)]
                       (or (cljstr/ends-with? x ".pdf")
                           (cljstr/ends-with? x ".png"))))
(spec/def :tune/href tune-href?)
(spec/def :numeric/content (spec/cat :numberic-str parse-long))

(spec/def :tune/attrs (spec/keys :req-un [:tune/href]))
(spec/def ::tune (spec/cat :data (spec/* (spec/alt :link (spec/keys :req-un [:a/tag :tune/attrs])
                                                   :text non-blank-string?
                                                   ;:space #{" "}
                                                   ))
                           :number (spec/keys :req-un [:font/tag
                                                       :numeric/content])))

(spec/def :td/content (spec/* ::tune #_(spec/alt :tune ::tune
                                        ;:blank blank-string-but-single-space?
                                        ;:break (spec/keys :req-un [:br/tag])
                                        )))


(def default-settings {:scraper/src-address "https://vitrifolk.fr/partitions/partitions-canada.html"
                       :scraper/tunes-address "https://vitrifolk.fr/partitions"
                       :scraper/dst-directory "downloads"
                       :process/init-crop-margin 0.02
                       :process/required-scale-change 0.7
                       :process/max-rel-height 1.4
                       :process/output-prefix "output"
                       :process/paper-width-meters 0.21
                       :render/frame? false
                       :render/scale 0.92
                       :process/bin-pack? true
                       })

(defn relative-height [[w h]]
  (/ h w))

(defn fetch-file [address]
  (let [req 
        (http-client/get address {:as :byte-array
                                        ;:throw-exceptions false
                                  })]
    (if (= (:status req) 200)
      (:body req)
      (throw (ex-info "Failed to fetch" {:req req})))))

(defn download-file [src-address dst-filename]
  (let [bytes (fetch-file src-address)]
    (with-open [w (io/output-stream dst-filename)]
      (.write w bytes))))



(defn list-table-cells [dst hickory-data]
  (if (map? hickory-data)
    (if (= :td (:tag hickory-data))
      (conj dst hickory-data)
      (reduce list-table-cells dst (:content hickory-data)))
    dst))

(defn useless-value? [x]
  (or (blank-string? x)
      (and (map? x)
           (let [tag (:tag x)]
             (= tag :br)))))


(defn clean-content [x]
  (vec (filter (complement useless-value?) x)))

(defn segment-tunes [data]
  (loop [src data
         tune []
         dst []]
    (if (empty? src)
      dst
      (let [[x & src] src
            tune (conj tune x)]
        (if (tune-number? x)
          (recur src [] (conj dst tune))
          (recur src tune dst))))))

(defn filename-extension [filename]
  (if-let [index (cljstr/last-index-of filename ".")]
    (subs filename (inc index))))

(defn get-links [tune-data]
  (transduce
   (comp (filter (fn [x] (and (map? x)
                              (= :a (:tag x))
                              (tune-href? (href x)))))
         (map (fn [x] (let [s (href x)]
                        {:link s
                         :ext (filename-extension s)}))))
   conj
   []
   tune-data))

(defn acc-content-strings [dst data]
  (cond
    (string? data) (conj dst data)
    (sequential? data) (reduce acc-content-strings dst data)
    (map? data) (acc-content-strings dst (:content data))
    :default (throw (ex-info "Cannot acc" {:data data}))))

(defn get-title [tune-data]
  (apply str (acc-content-strings [] tune-data)))

(defn parse-tune [tune-data]
  {:index (tune-number? (last tune-data))
   :links (get-links tune-data)
   :title (get-title (butlast tune-data))})

(defn parse-td [td-content]
  (map parse-tune (segment-tunes (clean-content td-content))))

(defn flatten-tune [tune]
  (let [tune-data (dissoc tune :links)]
    (mapv (fn [link version-index]
            (merge link
                   tune-data
                   {:version version-index}))
          (:links tune)
          (range))))

(defn tune-filename
  ([tune]
   (tune-filename tune ""))
  ([tune decoration]
   (format "tune%06d_v%02d%s.%s"
           (:index tune)
           (:version tune)
           decoration
           (cljstr/lower-case (:ext tune)))))

(defn custom-tune-filename
  ([tune decoration settings]
   (io/file (:scraper/dst-directory settings)
            (custom-tune-filename tune decoration)))
  ([tune decoration]
   (format "tune%06d_v%02d_%s"
           (:index tune)
           (:version tune)
           decoration)))

(defn full-tune-filename
  ([tune settings]
   (full-tune-filename tune "" settings))
  ([tune suf settings]
   (io/file (:scraper/dst-directory settings)
            (tune-filename tune suf))))


(defn full-address? [x]
  (cljstr/starts-with? x "http"))

(defn tune-address [tune settings]
  (let [addr (:link tune)]
    (if (full-address? addr)
      addr
      (str (:scraper/tunes-address settings) "/" (:link tune)))))

(defn analyze-png [filename]
  (let [data (:out (shell/sh "convert" filename "-format" "%w %h" "info:"))]
    {:size (filter identity (map parse-long (cljstr/split data #" ")))}))

(def example-pdf "/home/jonas/prog/clojure/jig-compiler/downloads/tune020146_v00.pdf")
(def example-pdf2 "/home/jonas/prog/clojure/jig-compiler/testdata/tune020151_v04-crop.pdf")

(defn pdfinfo-parse-kv [s]
  (let [n (cljstr/index-of s ":")]
    [(cljstr/trim (subs s 0 n))
     (cljstr/trim (subs s (inc n)))]))

(defn pdf-properties [filename]
  (transduce
   (map pdfinfo-parse-kv)
   conj
   {}
   (cljstr/split-lines (:out (shell/sh "pdfinfo" filename)))))

(defn parse-pdf-size [s]
  (let [pts (cljstr/index-of s "pts")
        s (subs s 0 pts)
        x (cljstr/index-of s "x")]
    [(parse-double (subs s 0 x))
     (parse-double (subs s (inc x)))]))

(defn analyze-pdf [filename]
  (let [props (pdf-properties filename)
        raw-page-size (get props "Page size")]
    {;;:pdf-properties props
     :raw-page-size raw-page-size
     :size (parse-pdf-size raw-page-size)
     :pages (parse-long (get props "Pages"))}))

(defn analyze-tune [tune settings]
  (let [filename (.getAbsolutePath (full-tune-filename tune settings))]
    (merge tune
           (case (:ext tune)
             "png" (analyze-png filename)
             "pdf" (analyze-pdf filename)))))

(defn multiple-pages-tune? [x]
  (if-let [p (:pages x)]
    (< 1 p)))

(defn filter-by-extension [tunes ext]
  {:pre [(string? ext)]}
  (filter (fn [x] (= ext (:ext x))) tunes))


(defn check-output [& args]
  (let [out (apply shell/sh args)]
    (if (= 0 (:exit out))
      (:out out)
      (throw (ex-info "Failed to run shell command"
                      {:args args
                       :out out})))))

(defn preprocessed-filename [tune settings]
  (custom-tune-filename tune "preprocessed.json" settings))

(defn preprocess-pdf [tune settings]
  {:pre [(= "pdf" (:ext tune))]}
  (let [src-filename (.getAbsolutePath (full-tune-filename tune settings))
        src-info (analyze-pdf src-filename)
        [src-width src-height] (:size src-info)
        bottom-margin-removed (.getAbsolutePath (full-tune-filename tune "_bottom_removed.pdf" settings))
        suf "_processed"
        processed-filename (.getAbsolutePath (full-tune-filename tune suf settings))
        _ (check-output "pdfcrop" src-filename
                        "--margins"
                        (str "0 0 0 " (int (* (- (:process/init-crop-margin settings))
                                              src-height)))
                        bottom-margin-removed)
        _ (check-output "pdfcrop" bottom-margin-removed
                        processed-filename)
        tight-info (analyze-pdf processed-filename) ;;  0.7
        scale-change (/ (-> tight-info :size second)
                        (-> src-info :size second))
        data (if (< scale-change (:process/required-scale-change settings))
               (merge tight-info
                      {:scale-change scale-change
                       :processed-filename (tune-filename tune suf)})
               (merge src-info
                      {:processed-filename (tune-filename tune)}))]
    (spit (preprocessed-filename tune settings)
          (cheshire/generate-string data))
    data))

(defn compute-density-pixels-per-cm [width-pixels settings]
  (let [width-meters (:process/paper-width-meters settings)
        pixels-per-meter (/ width-pixels width-meters)]
    (int (Math/round (* 0.01 pixels-per-meter)))))


(defn preprocess-png [tune settings]
  (let [src-filename (.getAbsolutePath (full-tune-filename tune settings))
        suf "processed.pdf"
        processed-filename (.getAbsolutePath (custom-tune-filename tune suf settings))
        info (analyze-png src-filename)
        size (:size info)
        [w h] size
        density (str (compute-density-pixels-per-cm w settings))]
    (println "   * Size" size "and density" density)
    (check-output "convert" src-filename "-size" (str w " " h)
                  "-units" "pixelspercentimeter"
                  "-density" density
                  processed-filename)
    (merge (analyze-pdf processed-filename)
           {:processed-filename (custom-tune-filename tune suf)})))

(defn preprocess [tune settings]
  (let [data (case (:ext tune)
               "pdf" (preprocess-pdf tune settings)
               "png" (preprocess-png tune settings))]
    (spit (preprocessed-filename tune settings)
          (cheshire/generate-string data))
    data))

(defn load-json [filename]
  (-> filename
      slurp
      (cheshire/parse-string true)))

(defn disp-group [group]
  (println "GROUP")
  (doseq [tune group]
    (println "  *" (:title tune) "  rh=" (:relative-height tune))))

(defn count-key
  ([dst] dst)
  ([dst k]
   (update dst k (fn [value] (inc (or value 0))))))


(defn multiversion-inds [tunes]
  (transduce
   (comp (map (fn [[k v]]
                (if (< 1 v)
                  k)))
         (filter identity))
   conj
   #{}
   (transduce
    (map :index)
    count-key
    {}
    tunes)))

(defn load-preprocessed-data [tunes settings]
  (for [tune tunes]
    (assoc tune :prep (load-json (preprocessed-filename tune settings)))))

(defn tune-weight [tune]
  (-> tune
      :prep
      :size
      relative-height))

(defn group-pages-naive [prep-tunes settings]
  (loop [tunes prep-tunes
         group []
         groups []
         rel-height 0]
    (if (empty? tunes)
      (let [result (conj groups group)]
        (assert (= (count prep-tunes)
                   (apply + (map count result))))
        result)
      (let [[tune & tunes] tunes
            rh (tune-weight tune)
            new-height (+ rel-height rh)]
        (if (<= new-height (:process/max-rel-height settings))
          (recur tunes (conj group tune) groups new-height)
          (do
            ;(disp-group group)
            (recur tunes [tune] (conj groups group) rh)))))))

(defn group-pages [tunes settings]
  (let [tunes (load-preprocessed-data tunes settings)
        mv-inds (if (:process/bin-pack? settings)
                  (multiversion-inds tunes)
                  (set (map :index tunes)))
        packed-tunes (filter (comp (complement mv-inds) :index) tunes)
        ordered-tunes (filter (comp mv-inds :index) tunes)
        packed-single (bp/pack-bins (:process/max-rel-height settings)
                                    tune-weight
                                    packed-tunes)
        packed-multi (group-pages-naive ordered-tunes settings)]
    (println "packed-tunes" (count packed-tunes))
    (println "packed-single" (count packed-single))
    (reduce into [] [packed-single packed-multi])))

(defn group-filename [index settings]
  (io/file (:process/output-prefix settings)
           (format "page_%02d.pdf" index)))

(defn build-group-page [index group settings]
  (let [filenames (for [tune group]
                    (let [file (io/file
                                (:scraper/dst-directory settings)
                                (:processed-filename (load-json (preprocessed-filename tune settings))))]
                      (if (not (.exists file))
                        (throw (ex-info "Missing file"
                                        {:file file
                                         :tune tune})))
                      (.getAbsolutePath file)))
        output-filename (group-filename index settings)]
    (io/make-parents output-filename)
    (let [out (apply shell/sh (flatten ["pdfjam"
                                        "--a4paper"
                                        "--frame" (if (:render/frame? settings) "true" "false")
                                        "--no-landscape"
                                        "--no-tidy"
                                        "--nup" (format "1x%d" (count filenames))
                                        filenames
                                        "--scale" (str (:render/scale settings))
                                        "--outfile" (.getAbsolutePath output-filename)]))]
      (when (not= 0 (:exit out))
        (println "FILENAMES:")
        (doseq [f filenames]
          (println "  *" f))
        (println (:err out))
        (throw (ex-info "Failed to build page"
                        {:out out}))))))

(defn build-groups [groups settings]
  (doseq [[index group] (map vector (range) groups)]
    (do
      (println (format "Render page %d/%d" (inc index) (count groups)))
      (build-group-page index group settings)))
  (println "Done"))

(defn concatenate-group-pages [groups settings]
  (let [catted-filename (str (:process/output-prefix settings) ".pdf")
        numbered-filename (str (:process/output-prefix settings) "_numbered.pdf")]
    (apply shell/sh (flatten ["pdftk"
                              (for [i (range (count groups))]
                                (.getAbsolutePath (group-filename i settings)))
                              "cat"
                              "output"
                              catted-filename]))
    (shell/sh "pdfjam" "--outfile" numbered-filename "--pagecommand" "'{}'" catted-filename)))

(defn find-tune-by-index [index tunes]
  (first (filter #(= index (:index %)) tunes)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Interface
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-page-data [settings]
  (-> settings
      :scraper/src-address
      (http-client/get {:as "windows-1252"})
      :body
      hickory/parse
      hickory/as-hickory))

(defn parse-page-data [data]
  (sort-by
   :index
   (transduce
    (comp (map (comp parse-td :content))
          cat
          (map flatten-tune)
          cat)
    conj
    []
    (list-table-cells [] data))))


(defn download-tunes [tunes settings]
  (doseq [tune tunes]
    (let [src-address (tune-address tune settings)
          dst-filename (full-tune-filename tune settings)]
      (if (.exists dst-filename)
        (println "Already downloaded" dst-filename)
        (do
          (println "Downloading" dst-filename "from" src-address)
          (io/make-parents dst-filename)
          (download-file src-address dst-filename)))))
  (println "Done!"))

(defn preprocess-tunes
  ([tunes settings]
   (preprocess-tunes tunes false settings))
  ([tunes force? settings]
   (doseq [tune tunes]
     (let [fname (preprocessed-filename tune settings)
           fname-label (.getAbsolutePath fname)]
       (if (and (not force?)
                (.exists fname))
         (println "Already processed" fname-label)
         (do
           (println "Process" fname-label)
           (preprocess tune settings)))))
     (println "Done!")))

(defn full-process []
  (let [settings default-settings]
    (let [log-msg (fn [x] (println "------------------------------" x))
          _ (log-msg "Get page")
          page-data (get-page-data settings)
          _ (log-msg "Parse page")
          tunes (parse-page-data page-data)
          _ (log-msg "Download tunes")
          _ (download-tunes tunes settings)
          _ (log-msg "Preprocess tunes")          
          _ (preprocess-tunes tunes settings)
          _ (log-msg "Optimize page layout")          
          groups (group-pages tunes settings)
          _ (log-msg "Build pages")                    
          _ (build-groups groups settings)
          _ (log-msg "Concatenate pages")                    
          _ (concatenate-group-pages groups settings)]
      (println "---------------- DONE"))))

(defn -main [& args]
  (full-process))

(comment

  
  ;;;;(def problematic-tune (find-tune-by-index 20162 tunes))   
  (def page-data (get-page-data default-settings))
  (def tunes (parse-page-data page-data))

  (download-tunes tunes default-settings)

  (def pdfs (filter-by-extension tunes "pdf"))
  (def pngs (filter-by-extension tunes "png"))
  (def first-pdf (first pdfs))
  (def first-png (first pngs))

  (preprocess-tunes tunes true default-settings)
  (def groups (group-pages tunes default-settings))
  (build-groups groups default-settings)
  (concatenate-group-pages groups default-settings)


  
  )
