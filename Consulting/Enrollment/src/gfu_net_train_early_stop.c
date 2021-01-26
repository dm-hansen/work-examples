#include <stdio.h>
#include <string.h>
#include <float.h>
#include <limits.h>
#include "doublefann.h"

// Define algorithms and functions -- FANN_ELLIIOT seems to work best
#define TRAIN_ALGORITHM FANN_TRAIN_RPROP
#define HIDDEN_FUNC FANN_ELLIOT
#define OUTPUT_FUNC FANN_ELLIOT

// Define parameters
#define NUM_LAYERS 3
#define DESIRED_ERROR 0.001
// Set to 1 as the callback is what stops early
#define EPOCHS_BETWEEN_REPORTS 1
#define MAX_EPOCHS 2000


#define MAX_NETS 5 
#define MAX_CALLS 15


#define NUM_ARGS 3

struct fann_train_data *train_data = NULL;
struct fann_train_data *test_data = NULL;
struct fann* best_network;
double min_error = DBL_MAX;
double current_error = DBL_MAX;
double best_test = DBL_MAX;
double best_train = DBL_MAX;
int epochs = 0;



int FANN_API test_callback(struct fann *ann, struct fann_train_data *train,
      unsigned int max_epochs, unsigned int epochs_between_reports,
      float desired_error, unsigned int epochs) {
      
   float mse, test_mse, train_mse;
   static int num_calls = 0;
   // Check the current network and see if it's error has increased. If
   // it has, quit
   // Test the ANN on the test data
   test_mse =  fann_test_data(ann, test_data);
   train_mse = fann_test_data(ann, train_data);
   mse = train_mse + test_mse;
printf("%d\t%d\t%f\t%f\t%f\t%f\n",++epochs,num_calls, current_error, mse, test_mse, train_mse);
   // If error is higher, quit, else keep going
   if (( train_mse > best_train || test_mse > best_test) && (++num_calls > MAX_CALLS) ) {
      num_calls = 0;
      return EXIT_FAILURE;;
   }
   else {
      if (test_mse < best_test) best_test = test_mse;
      if (train_mse < best_train) best_train = train_mse;
      if (train_mse <= best_train && test_mse <= best_test ) {
         current_error = mse;
         num_calls = 0;
      }
      return EXIT_SUCCESS;
   }
}






/*
 * Simple program that trains a model given the training data
 *
 * Arguments 
 *    0 : Datafile
 *    1 : Network argv[1]
 */
int main(int argc, char* argv[]) {
      
   int i;
   struct fann_train_data *data = NULL;
   struct fann *network = NULL;
   fann_type network_prediction;
   long numData;


   // Sufficient Arguments?
   if (argc != NUM_ARGS) {
      fprintf(stderr, "Insufficient arguments\nUsage: %s <training data file> <output network filename>\n", argv[0]);
      exit(-1);
   }


   // Read the training data file
   if ( !(data = fann_read_train_from_file(argv[1])) ) {
      perror("Error opening data file--- ABORTING.\n");
      exit(-1);
   }
   numData = fann_length_train_data(data);


   for (i=0; i<MAX_NETS; i++) {
      current_error = DBL_MAX;
      best_test = DBL_MAX;
      best_train = DBL_MAX;

      // Create a network
      network = fann_create_standard(NUM_LAYERS, 
            fann_num_input_train_data(data), 
            // hidden as 1/2 * inputs seems good rule-of-thumb
            fann_num_input_train_data(data)/2, 
            fann_num_output_train_data(data));

      fann_init_weights(network, data);
      fann_set_callback(network, test_callback);

      fann_set_input_scaling_params(network, data, 0.0, 1.0);

      // Shuffle the data and split into training and validation sets
      fann_shuffle_train_data(data);
      train_data = fann_subset_train_data(data, 0, numData/2);
      test_data = fann_subset_train_data(data, numData/2, numData/2);
      fann_scale_train(network, train_data);
      fann_scale_train(network, test_data);


      // Set the training algorithm and hidden/output functions
      fann_set_training_algorithm(network, TRAIN_ALGORITHM);
      fann_set_activation_function_hidden(network, HIDDEN_FUNC);
      fann_set_activation_function_output(network, OUTPUT_FUNC);

      // Train!
      fann_train_on_data(network, train_data, MAX_EPOCHS, EPOCHS_BETWEEN_REPORTS, DESIRED_ERROR);

      // See if this network has the best MSE
      if (current_error < min_error) {
         if (best_network) fann_destroy(best_network);
         best_network = fann_copy(network);
         min_error = current_error;
      }
      printf("MSE: %f\n",min_error);

      fann_destroy_train(test_data);
      fann_destroy_train(train_data);
   }

   // Save the network we've trained
   fann_save(best_network, argv[2]);

   return EXIT_SUCCESS;
}
