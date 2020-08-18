(ns helloworld
  (:require [clonfig.core :as cfg]))

(cfg/defconfig *foo* "foo")

(defn -main
  [& args]
  (cfg/init! args)
  (println *foo*))