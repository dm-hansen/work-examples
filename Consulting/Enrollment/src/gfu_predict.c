#include <stdio.h>
#include <string.h>
#include "doublefann.h"

#define NUM_ARGS 3
#define MAX_STRING 1024


int main(int argc, char* argv[])
{
   int i;
   struct fann_train_data *data;
   struct fann *network;
   fann_type prediction;

   // Sufficient Arguments?
   if (argc != NUM_ARGS)
   {
      fprintf(stderr, "Insufficient arguments\nUsage: %s <network file> <data file>\n", argv[0]);
      exit(-1);
   }

   
   // Open the network file 
   if ( !(network = fann_create_from_file(argv[1])) )
   {
      perror("Error creating network --- ABORTING.\n");
      return -1;
   }

   // Open the data file
   if ( !(data = fann_read_train_from_file(argv[2])) )
   {
      fprintf(stderr, "Error opening data file %s\n", argv[2]);
      return -1;
   }

   // Iterate over every entry in the datafile and make a prediction
   for (i=0; i<fann_length_train_data(data); i++)
   {
      fann_scale_input(network, data->input[i]);
      prediction = fann_run(network, data->input[i])[0];
      printf("%22.20lf\n", prediction);
   }

   return EXIT_SUCCESS;
}
