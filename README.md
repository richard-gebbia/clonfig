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

Then, you can invoke this program like so:

```
clj -m my-cool-program.core --foo 'Hello, world!'
```

and you'll see the familiar `Hello, world!` message printed to the screen.

## Coordinates

[![Clojars Project](https://img.shields.io/clojars/v/richard-gebbia/clonfig.svg)](https://clojars.org/richard-gebbia/clonfig)

### Leiningen/Boot
```
[richard-gebbia/clonfig "0.1.0"]
```

### deps.edn
```
richard-gebbia/clonfig {:mvn/version "0.1.0"}
```

### Gradle
```
compile 'richard-gebbia:clonfig:0.1.0'
```

### Maven
```
<dependency>
  <groupId>richard-gebbia</groupId>
  <artifactId>clonfig</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Options

There are a number of options that you can add to a `defconfig` to customize it to your liking.

#### cli-tools options

This library makes use of the excellent [clojure/tools.cli](https://github.com/clojure/tools.cli) library. Any options you pass to `defconfig` will automatically be propagated as `cli-options`. This means you can do things like:

```clojure
(defconfig *foo*
  :default "bar"
  :default-desc "What did you expect?"
  :validate [#(<= 3 (len %)) "Must have at least 3 characters."]
  :required true)
```

to forward the extra options over to `clojure/tools.cli`.

#### `:env-var`

Normally (on the JVM only currently), if a `defconfig` is not explicitly set via the command line, it reads its value from the environment. This option allows you to set the name of the environment variable to read from.

```clojure
(defconfig *foo*
  :env-var "foo")
```

Now when you run the example above, you can do this:
```
foo='Hello, world!' clj -m my-cool-program.core
```

By default, this value is set to the following transformation of the var name:
- non-alphanumeric characters are removed
- dashes (`-`) are changed to underscores (`_`)
- alphabetic characters are uppercased
- all numeric characters are stripped from the front of the name

e.g.
- `*foo*` looks for `FOO`
- `*foo-bar*` looks for `FOO_BAR`
- `*12foo-bar34*` looks for `FOO_BAR34`

#### `:short`

Add a short, ideally one-character switch for the `defconfig` on the command line.

```clojure
(defconfig *foo*
  :short "-f")
```

Now when you run the example above, you can use this instead:
```
clj -m my-cool-program.core -f 'Hello, world!'
```

By default, this is `nil`, meaning there isn't a short form to determine the `defconfig`'s value from the command line.

#### `:long`

Customize the long, usually double-dashed switch for the `defconfig` on the command line.

```clojure
(defconfig *foo*
  :long "--bar")
```

Now when you run the example above, you'll have to use this to get the same result:
```
clj -m my-cool-program.core --bar 'Hello, world!'
```

By default, this prepends `--` to the name of the `defconfig` with all `*` characters removed.

#### `:boolean?`

Use this to indicate to the `clojure/tools.cli` parser that this `defconfig` is a boolean and therefore should only check for the presence of the switch rather expecting a corresponding value.

Saying this:

```clojure
(defconfig *foo*
  :boolean? true)
```

means that the user can call your program like so:
```
clj -m my-cool-program.core --foo
```

to indicate that `*foo*` should be `true`. The invocation would not have to look like:

```
clj -m my-cool-program.core --foo true
```

By default, this is `false`, unless `:default` is set to `true` or `false`.

#### `:source`

Control how the variable gets its value. There are three possible values for this option:
- `clonfig.core/from-env-var-only` to indicate that the `defconfig`'s value should only be read from an environment variable (and not from the command line)
- `clonfig.core/from-cmd-line-only` to indicate that the `defconfig`'s value should only be read from the command line (and not from an environment variable)
- `clonfig.core/from-both` to indicate that the `defconfig`'s value should be read from an environment variable unless it's set via the command line

e.g. If this is defined in the example above:

```clojure
(defconfig *foo*
  :source from-env-var-only)
```

then the following invocation:
```
FOO=hello clj -m my-cool-program.core --foo world
```

would print `hello`. If `:source` was set to `from-cmd-line-only`, then it would print `world` instead.

By default, this option is set to `from-both`.

#### `:default`

This acts just like `:default` in `clojure/tools.cli` and is ultimately passed to `clojure.tools.cli/parse-opts`. If a value isn't specified either in the environment or via the command line, it will be set to the `:default` value.

```clojure
(defconfig *foo*
  :default "wow")
```

Now when you run the example above like so:
```
clj -m my-cool-program.core
```

it will print `wow`.

#### `:parse-fn`

This acts just like [`:parse-fn` in `clojure/tools.cli`](https://github.com/clojure/tools.cli#option-argument-validation). When a value is read from either the environment or from the command line, the `parse-fn` is run to parse it from a string to a value.

```clojure
(defconfig *foo*
  :parse-fn #(Integer/parseInt %))
```

Now when you run the example above with the following command line:
```
clj -m my-cool-program.core --foo 3
```

`*foo*` will have the value `3` rather than `"3"`.

#### `:spec`

Add spec validation to the `defconfig` after it's been parsed via `:parse-fn`. See the "return value" section below.

## Return value

The return value of `init!` is a map with two keys in it:
- `:cli-errors`, which is precisely the `:errors` value in the call to `clojure.tools.cli/parse-opts`; and 
- `:spec-failures`, which is a map where:
  - the keys are the name of the `defconfig` that failed spec validation, as a keyword, without `*`s
  - the values are the `explain-data` of the failing spec

e.g.

```clojure
(defconfig *foo*
  :spec #{"yes" "no" "maybe"})
```

If a program with this `defconfig` was invoked like so:

```
clj -m --foo totes --nonexistent
```

the call to `init!` would return:

```clojure
{:cli-errors ["Unknown option: \"--nonexistent\""],
 :spec-failures
 {:foo
  #:clojure.spec.alpha{:problems
                       [{:path [],
                         :pred #{"maybe" "yes" "no"},
                         :val "totes",
                         :via [],
                         :in []}],
                       :spec #{"maybe" "yes" "no"},
                       :value "totes"}}}
```