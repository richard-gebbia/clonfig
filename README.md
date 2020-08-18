# clonfig

Trying to steal a [good idea from the Chapel programming language](https://chapel-lang.org/docs/master/users-guide/base/configs.html) and implement it in Clojure.

The core of the idea comes from the notion that command-line arguments are essentially a program's inputs. If you can specify a program's inputs declaratively, the command-line interface should be an artifact of that.

Example usage:

```clojure
(ns my-cool-program.core
    (:require [clonfig.core as cfg]))

(cfg/defconfig *foo*
    "The foo input to this very cool program")

(defn -main
    [& args]
    (cfg/init! args)
    (println *foo*))
```