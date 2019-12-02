# About
This is a project in Clojure that displays the data structure for data made of arbitrary nesting of maps, sets, vectors, and sequences. It probably works in ClojureScript, but I've not tried. The multivating use case of the project is to understand andmanipulate the complicated structures returned from a GraphQL query. I've not made this tool for industrial use, so deal with bugs at your own risk. I welcome bug reports or pull requests.

# Use case examples
Since this tool is only meant to be used in the REPL, I have not tried to package it up. Just put it in your source path and require the namespace.
```clojure
user=> (require '[check-type :refer [check-default check pcheck]])
```

Using it on a non-collection type is the same as `(type)`.
```clojure
user=> (check 5)
java.lang.Long
```
Using it on a flat collection basically calls `(check)` on the contents and puts it in the same collection type.
```clojure
user=> (check [5 3 7 8 6 10])
[java.lang.Long]
```
It combines maps while keeping keys separate (configurable).
```clojure
user=> (check [{:a 4, :b 3} {:a 8 "b" 9}])
[{:a java.lang.Long, :b java.lang.Long, "b" java.lang.Long}]
```
Arbitrarily deep data structures are merged recursively.
```clojure
user=> (check [{:a [{:a 3 :b ["a" "b" "c"]}] :b "b"}
               {:a [{                        :b ["d"]}]}])
[{:a [{:a java.lang.Long, :b [java.lang.String]}], :b java.lang.String}]
```
Mixed type collections are handled like the following. Since map values don't have a nice way to be displayed in a sequential collection, they are indicated with `[:union (types...)]`.
```clojure
user=> (check [4 "string"])
[java.lang.Long java.lang.String]
user=> (check [{:a 4} {:a "string"}])
[{:a [:union (java.lang.Long java.lang.String)]}]
```

# Configuration
To pass configuration parameters, use a map as the second parameter as shown below. Alternatively, the defaults are stored in a atomic map called `check-default` as defined below, which you can modify in your setup.
```clojure
(def check-default
    (atom {:eval-lazy :peek
           :combine-keys [number?]
           :combine-numbers true
           :preserve-vals [keyword?]
           :levels 5}))
```

## Recursion depth
To prevent cluttered output (and stack overflow), I've set a max recursion limit. It can be modified using the `:levels` parameter. Set it to negative for unlimited recursion.
```clojure
user=> (check [[[[[[:a]]]]]])
[[[[[clojure.lang.PersistentVector]]]]]
user=> (check [[[[[[:a]]]]]] {:levels 10})
[[[[[[:a]]]]]]
```

## Numeric types
Clojure has multiple types for numerical values. You can combine them into a single type like the following.
```clojure
user=> (check [3 3.5 4/3])
[java.lang.Long java.lang.Double clojure.lang.Ratio]
user=> (check [3 3.5 4/3] {:combine-numbers true})
[:number]
```

## Combining keys
There are cases where you might want to combine different keys in a map, like when the key is a customer ID. `:combine-keys` accepts a list of predicate functions, and the to a map is combined if any of the functions return true.
```clojure
user=> (check [{3 "3" 4 "4"} {"strkey" "strval"}]
              {:combine-keys []})
[{3 java.lang.String, 4 java.lang.String, "strkey" java.lang.String}]
user=> (check [{3 "3" 4 "4"} {"strkey" "strval"}]
              {:combine-keys [number?]})
[{java.lang.Long java.lang.String, "strkey" java.lang.String}]
user=> (check [{3 "3" 4 "4"} {"strkey" "strval"}]
              {:combine-keys [(constantly true)]})
[{java.lang.Long java.lang.String, java.lang.String java.lang.String}]
```

## Preserve special values
There are certain values you might not want to convert to types, like keywords. The `:preserve-vals` accepts a list of predicate functions like `:combine-keys`.
```clojure
user=> (check {:key :val} {:preserve-vals []})
{:key clojure.lang.Keyword}
user=> (check {:key :val} {:preserve-vals [keyword?]})
{:key :val}
```

## Lazy sequences
Since lazy sequences can be infinite or computationally expensive to compute, you can choose to not evaluate a lazy sequence, only evaluate the first member, or evaluate the entire sequence.
```clojure
user=> (check (map inc [1 2.5 3/4])
              {:combine-numbers false :eval-lazy :none})
clojure.lang.LazySeq
user=> (check (map inc [1 2.5 3/4])
              {:combine-numbers false :eval-lazy :peek})
(java.lang.Long)
user=> (check (map inc [1 2.5 3/4])
              {:combine-numbers false :eval-lazy :all})
(clojure.lang.Ratio java.lang.Double java.lang.Long)
```

# pprint results
For convenience, I created a helper function call `pcheck` which has the same signature as `check` and passes the output to `pprint`.
