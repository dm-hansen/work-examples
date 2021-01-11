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
#define EPOCHS_BETWEEN_REPORTS 10
#define MAX_EPOCHS 2000

#define NUM_ARGS 3

/*
 * Simple program that trains a model given the training data
 *
 * Arguments 
 *    0 : Datafile
 *    1 : Network argv[1]
 */
int main(int argc, char* argv[])
{
   int i;
   struct fann_train_data *data;
   struct fann *network;
   FILE *network_file;
   fann_type network_min_value = DBL_MAX;
   fann_type network_max_value = 0.0;
   fann_type network_prediction;


   // Sufficient Arguments?
   if (argc != NUM_ARGS)
   {
      fprintf(stderr, "Insufficient arguments\nUsage: %s <training data file> <output network filename>\n", argv[0]);
      exit(-1);
   }


   // Read the training data file
   if ( !(data = fann_read_train_from_file(argv[1])) )
   {
      perror("Error opening data file--- ABORTING.\n");
      exit(-1);
   }

   // Create a network
   network = fann_create_standard(NUM_LAYERS, 
                  fann_num_input_train_data(data), 
                  // hidden as input/2 seems good rule-of-thumb
                  fann_num_input_train_data(data)/2, 
                  fann_num_output_train_data(data));

   // Set the training algorithm and hidden/output functions
   fann_set_training_algorithm(network, TRAIN_ALGORITHM);
   fann_set_activation_function_hidden(network, HIDDEN_FUNC);
   fann_set_activation_function_output(network, OUTPUT_FUNC);

   // Train!
   fann_train_on_data(network, data, MAX_EPOCHS, 
                      EPOCHS_BETWEEN_REPORTS, DESIRED_ERROR);

   fann_save(network, argv[2]);


   // Now run the training data through the network to determine the
   // min/max outputs of the network. We'll append those to the file
   // holding the network so we can use that during prediction to
   // normalize the data to a common scale
   for(i = 0; i < fann_length_train_data(data); i++)
   {
      network_prediction = fann_run(network, data->input[i])[0];
      if (network_prediction < network_min_value)
      {
         network_min_value = network_prediction;
      }
      if (network_prediction > network_max_value)
      {
         network_max_value = network_prediction;
      }
   }

   // Open the network file and append the min and max values
   network_file = fopen(argv[2], "a");
   fprintf(network_file," %22.20lf %22.20lf",network_min_value, network_max_value);
   fclose(network_file);



   // Clean up
   fann_destroy_train(data);
   fann_destroy(network);

   return EXIT_SUCCESS;
}
