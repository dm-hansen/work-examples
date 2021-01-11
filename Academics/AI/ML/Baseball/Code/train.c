#include <stdio.h>
#include <string.h>
#include <float.h>
#include "fann.h"

/*****************/
// Define parameters

// Best for pitchers?
#define OUTPUT_FUNC FANN_COS_SYMMETRIC
#define HIDDEN_FUNC FANN_SIGMOID
#define TRAIN_ALGORITHM FANN_TRAIN_RPROP

// best for batters?
//#define OUTPUT_FUNC FANN_SIGMOID
//#define HIDDEN_FUNC FANN_ELLIOT
//#define TRAIN_ALGORITHM FANN_TRAIN_RPROP



#define NUM_LAYERS 3
#define DESIRED_ERROR 0.000001
#define EPOCHS_BETWEEN_REPORTS 100
#define MAX_EPOCHS 2000
#define MAX_NEURONS 50
/*********************/

#define NUM_ARGS 3

/*
 * Simple program that trains a model given the training data
 *
 * Arguments 
 *    1 : Data filename
 *    2 : FANN Network filename
 */
int main(int argc, char* argv[])
{
   struct fann_train_data *data;
   struct fann* network;


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

   fann_set_input_scaling_params(network, data, 0.0, 1.0);
   fann_scale_train(network, data);

   // Set the training algorithm and hidden/output functions
   fann_set_training_algorithm(network, TRAIN_ALGORITHM);
   fann_set_activation_function_hidden(network, HIDDEN_FUNC);
   fann_set_activation_function_output(network, OUTPUT_FUNC);

   // Train!
   fann_train_on_data(network, data, MAX_EPOCHS, EPOCHS_BETWEEN_REPORTS, DESIRED_ERROR);

   // Save the network we've trained
   fann_save(network, argv[2]);

   // Clean up
   fann_destroy_train(data);
   fann_destroy(network);
   return EXIT_SUCCESS;
}

/****

  THESE WERE SOME OF THE BEST FOR PITCHERS WHEN WE 
  ITERATED OVER ALL COMBINATIONS; BATTERS WERE MORE
  STRAIGHTFORWARD

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0126803908. Bit fail 32.
Epochs         2000. Current error: 0.0016278524. Bit fail 0.
hidden FANN_SIGMOID   output FANN_GAUSSIAN_SYMMETRIC    training FANN_TRAIN_INCREMENTAL



Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0113921352. Bit fail 35.
Epochs         2000. Current error: 0.0016003656. Bit fail 0.
hidden FANN_SIGMOID   output FANN_ELLIOT_SYMMETRIC    training FANN_TRAIN_INCREMENTAL


Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0116238771. Bit fail 29.
Epochs         2000. Current error: 0.0016404233. Bit fail 1.
hidden FANN_SIGMOID   output FANN_SIGMOID_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.2296620011. Bit fail 942.
Epochs         2000. Current error: 0.0007712754. Bit fail 0.
hidden FANN_SIGMOID   output FANN_COS_SYMMETRIC    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0116899731. Bit fail 32.
Epochs         2000. Current error: 0.0013953269. Bit fail 0.
hidden FANN_SIGMOID_STEPWISE   output FANN_SIGMOID_SYMMETRIC_STEPWISE    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0127912583. Bit fail 32.
Epochs         2000. Current error: 0.0010826297. Bit fail 0.
hidden FANN_SIGMOID_STEPWISE   output FANN_GAUSSIAN_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0118669020. Bit fail 35.
Epochs         2000. Current error: 0.0010714463. Bit fail 0.
hidden FANN_SIGMOID_STEPWISE   output FANN_ELLIOT_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0130384546. Bit fail 35.
Epochs         2000. Current error: 0.0008102444. Bit fail 0.
hidden FANN_SIGMOID_STEPWISE   output FANN_COS_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.2333044857. Bit fail 942.
Epochs         2000. Current error: 0.0009916164. Bit fail 0.
hidden FANN_SIGMOID_STEPWISE   output FANN_COS_SYMMETRIC    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0150925191. Bit fail 42.
Epochs         2000. Current error: 0.0026138965. Bit fail 0.
hidden FANN_SIGMOID_SYMMETRIC_STEPWISE   output FANN_LINEAR_PIECE_SYMMETRIC    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0150925191. Bit fail 42.
Epochs         2000. Current error: 0.0026138965. Bit fail 0.
hidden FANN_SIGMOID_SYMMETRIC_STEPWISE   output FANN_LINEAR_PIECE_SYMMETRIC    training FANN_TRAIN_RPROP


Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0214714296. Bit fail 66.
Epochs         2000. Current error: 0.0007940968. Bit fail 1.
hidden FANN_GAUSSIAN   output FANN_SIGMOID_SYMMETRIC_STEPWISE    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0122361099. Bit fail 26.
Epochs         2000. Current error: 0.0017189863. Bit fail 0.
hidden FANN_GAUSSIAN   output FANN_COS_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0162037052. Bit fail 49.
Epochs         2000. Current error: 0.0012468499. Bit fail 0.
hidden FANN_GAUSSIAN_SYMMETRIC   output FANN_SIGMOID_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

ax epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0150592821. Bit fail 42.
Epochs         2000. Current error: 0.0009561822. Bit fail 0.
hidden FANN_GAUSSIAN_SYMMETRIC   output FANN_SIGMOID_SYMMETRIC_STEPWISE    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.2099172771. Bit fail 940.
Epochs         2000. Current error: 0.0017084619. Bit fail 0.
hidden FANN_GAUSSIAN_SYMMETRIC   output FANN_GAUSSIAN_SYMMETRIC    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0133029670. Bit fail 41.
Epochs         2000. Current error: 0.0009258782. Bit fail 1.
hidden FANN_ELLIOT   output FANN_SIGMOID_SYMMETRIC_STEPWISE    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.2326388657. Bit fail 942.
Epochs         2000. Current error: 0.0008844953. Bit fail 0.
hidden FANN_ELLIOT   output FANN_GAUSSIAN_SYMMETRIC    training FANN_TRAIN_RPROP

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.0138548873. Bit fail 44.
Epochs         2000. Current error: 0.0008376514. Bit fail 0.
hidden FANN_ELLIOT   output FANN_COS_SYMMETRIC    training FANN_TRAIN_INCREMENTAL

Max epochs     2000. Desired error: 0.0000010000.
Epochs            1. Current error: 0.2335798889. Bit fail 942.
Epochs         2000. Current error: 0.0009840293. Bit fail 0.
hidden FANN_ELLIOT_SYMMETRIC   output FANN_COS_SYMMETRIC    training FANN_TRAIN_RPROP

*****/
