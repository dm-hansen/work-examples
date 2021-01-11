This is my OOPy solution to a "game of life" type of simulation
presented to Data Structures students as way of demonstrating the use of
basic data structures such as arrays and matrices as well as basic
principles of OOP.

In my Operating Systems course we revisit this simulation by making the
instances of each "animal" an autonomous thread and moving the
determination of how to change the state of the simulation into the
"animals." The difficult part of that task is that we require them to
use concurrency control (e.g., locking) to maintain "consitency" in a
non-deterministic multi-threaded simulation (e.g., a fox can not be 
eaten by more than one hound) while avoiding deadlock ("easily" done by
aquiring locks in order).
