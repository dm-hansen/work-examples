#include <stdio.h>
#include <string.h>
#include "fann.h"

#define NUM_ARGS 3

/*
 * Simple program that generates prediction based on the network and
 * input file provided.
 *
 * Arguments 
 *    1 : FANN network filename
 *    2 : Data filename
 * Output
 *    single prediction
 */
int main(int argc, char* argv[])
{
   int i;
   FILE *network_file;
   struct fann_train_data *data;
   struct fann *network;
   fann_type *calc_out;

   // Sufficient Arguments?
   if (argc != NUM_ARGS)
   {
      fprintf(stderr, "Insufficient arguments\nUsage: %s <FANN network file> <data file>\n", argv[0]);
      exit(-1);
   }

   // Attempt to read the network
   if( !(network = fann_create_from_file(argv[1])) )
   {
      perror("Error creating network --- ABORTING.\n");
      exit(-1);
   }

   // Read the datafile as training data to find out how many 
   // there are and to provide a simple way to iterate over the data set
   if ( !(data = fann_read_train_from_file(argv[2])) )
   {
      perror("Error opening datafile --- ABORTING.\n");
      exit(-1);
   }

//   fann_set_activation_function_output(network, FANN_THRESHOLD);

   fann_scale_train(network, data);
  
   // Generate a prediction for each row in the data file
   for (i=0; i<fann_length_train_data(data); i++)
   {
      // Predict and print the prediction
      calc_out = fann_run(network, data->input[i]);
      fann_descale_output(network, calc_out);
      printf("%22.20lf\n",calc_out[0]);
   }

   // Clean up after ourselves
   fann_destroy_train(data);
   fann_destroy(network);

   return EXIT_SUCCESS;
}
