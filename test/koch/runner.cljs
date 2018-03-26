(ns koch.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [koch.core-test]))
(doo-tests 'koch.core-test)
