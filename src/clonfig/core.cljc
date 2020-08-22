(ns clonfig.core
  (:require [clojure.string :as s]
            [clojure.tools.cli :as cli]))

(defonce ^:private registry
  (atom {}))

(defn defconfig-impl
  [var env-var short long desc id source & cli-options]
  (prn cli-options)
  (swap! registry assoc var {:env-var env-var
                             :id      id
                             :source  source
                             :cli-options (into [short long desc :id id] cli-options)}))

(def from-env-var-only ::from-env-var-only)
(def from-cmd-line-only ::from-cmd-line-only)
(def from-both ::from-both)

(defn env-var-ify
  "Takes a string that would make for a legal Clojure symbol and converts it into legal environment variable name."
  [s]
  (let [numeric #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9}]
    (->> s
         (map #(if (= \- %) \_ %))
         (filter #(re-find #"[\w\d]" (str %)))
         (drop-while numeric)
         (s/join))))

(defmacro defconfig
  "Defines a var that will contain the value of either an environment variable or command-line argument."
  [name & params]
  (let [desc       (if (string? (first params)) (first params) "")
        params     (if (string? (first params)) (rest params) params)
        params-map (apply hash-map params)
        
        {:keys [var-sym env-var default short long id boolean? source]
         :or {var-sym  name
              env-var  (s/replace (s/replace (s/upper-case (str name)) "-" "_") "*" "")
              id       (keyword (s/replace (str name) "*" ""))
              boolean? false
              source   from-both}
         :as args} params-map

        unstarred (s/replace (str var-sym) "*" "")
        long (or long (str "--" unstarred (if (not boolean?) (str " " (s/upper-case unstarred)))))]
    `(do (defonce ~(with-meta var-sym {:dynamic true}) ~default)
         (defconfig-impl (var ~var-sym) ~env-var ~short ~long ~desc ~id ~source ~@(mapcat identity args)))))

(defn init!
  [args]
  ; first set all the config values to their corresponding env-vars
  (let [reg @registry]
    #?(:clj (run! (fn [[var {:keys [env-var source]}]]
                    (when (or (= source from-env-var-only)
                              (= source from-both))
                      (alter-var-root var
                        (constantly (System/getenv env-var)))))
                  reg))
    ; then set the config values from the command-line args if they were specified
    (let [cli-options (mapv :cli-options (vals reg))
          parsed-opts (cli/parse-opts args cli-options)]
      (dorun (map (fn [[var {:keys [id source]}]]
                    (let [clarg (-> parsed-opts :options id)]
                      (when (and clarg
                                 (or (= source from-cmd-line-only)
                                     (= source from-both)))
                        (alter-var-root var
                          (constantly clarg)))))
                  reg))
      ; return any errors
      (:errors parsed-opts))))