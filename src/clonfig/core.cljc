(ns clonfig.core
  (:require [clojure.spec.alpha :as sp]
            [clojure.string :as s]
            [clojure.tools.cli :as cli]))

(defn env-var-ify
  "Takes a string that would make for a legal Clojure symbol and converts it into legal environment variable name."
  [s]
  (let [numeric #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9}]
    (->> s
         (map #(if (= \- %) \_ %))
         (filter #(re-find #"[\w\d]" (str %)))
         (drop-while numeric)
         (s/join))))

(def non-cli-tools-keys
  "Keys that only this library cares about and should not pass on to cli-tools"
  #{:var-sym
    :env-var
    :boolean?
    :source
    :short
    :long
    :parse-fn
    :spec})

(def from-env-var-only ::from-env-var-only)
(def from-cmd-line-only ::from-cmd-line-only)
(def from-both ::from-both)

(defonce ^:private registry
  (atom {}))

(defn defconfig-impl
  "Please don't call this. Call defconfig instead."
  [var env-var short long desc id source parse-fn spec & cli-options]
  (swap! registry assoc var {:env-var  env-var
                             :id       id
                             :source   source
                             :parse-fn parse-fn
                             :spec     spec
                             :cli-options (into [short long desc :id id] cli-options)}))

(defmacro defconfig
  "Defines a var that will contain the value of either an environment variable or command-line argument."
  [name & params]
  (let [desc       (if (string? (first params)) (first params) "")
        params     (if (string? (first params)) (rest params) params)
        params-map (apply hash-map params)
        
        {:keys [var-sym env-var default short long id boolean? source parse-fn spec]
         :or {var-sym  name
              env-var  (s/replace (s/replace (s/upper-case (str name)) "-" "_") "*" "")
              id       (keyword (s/replace (str name) "*" ""))
              boolean? false
              source   from-both
              parse-fn identity
              spec     any?}
         :as args} params-map

        unstarred (s/replace (str var-sym) "*" "")
        long (or long (str "--" unstarred (if (not boolean?) (str " " (s/upper-case unstarred)))))]
    `(do (defonce ~(with-meta var-sym {:dynamic true}) ~default)
         (defconfig-impl (var ~var-sym) ~env-var ~short ~long ~desc ~id ~source ~parse-fn ~spec
                         ~@(mapcat identity (apply dissoc args non-cli-tools-keys))))))

(defn init!
  [args]
  ;; first set all the config values to their corresponding env-vars
  (let [reg @registry]
    #?(:clj (run! (fn [[var {:keys [env-var source]}]]
                    (when (or (= source from-env-var-only)
                              (= source from-both))
                      (alter-var-root var
                        (constantly (System/getenv env-var)))))
                  reg))
    ;; then set the config values from the command-line args if they were specified
    (let [cli-options   (mapv :cli-options (vals reg))
          parsed-opts   (cli/parse-opts args cli-options)
          spec-failures (atom {})]
      (dorun (map (fn [[var {:keys [id source parse-fn spec]}]]
                    ;; get the string value from the command line
                    (let [clarg (-> parsed-opts :options id)]
                      (when (and clarg
                                 (or (= source from-cmd-line-only)
                                     (= source from-both)))
                        (alter-var-root var
                          (constantly clarg))))
                        
                    ;; parse the string into a Clojure value
                    (alter-var-root var parse-fn)

                    ;; validate the data with a spec
                    (let [explain-data (sp/explain-data spec (var-get var))]
                      (when explain-data
                        (swap! spec-failures assoc id explain-data))))
                  reg))
      ;; return any errors
      {:cli-errors (:errors parsed-opts)
       :spec-failures @spec-failures})))