import fann2.libfann as fann
import copy
import sys


# Define algorithms and functions -- FANN_ELLIIOT seems to work best
_TRAIN_ALGORITHM = fann.TRAIN_RPROP
_HIDDEN_FUNC = fann.ELLIOT
_OUTPUT_FUNC = fann.ELLIOT

# Define parameters
_NUM_LAYERS = 3

# Number of networks to try
_MAX_NETS = 5 
# How many epochs of stability before we call it good
_MAX_CALLS = 15


_NUM_ARGS = 3



if __name__ == '__main__':

   # Sufficient Arguments?
   if (len(sys.argv) != _NUM_ARGS):
      print 'Insufficient arguments\nUsage: ',sys.argv[0],' <training data file> <output network filename>\n'
      sys.exit(-1)


   # Read the training data file
   data = fann.training_data()
   data.read_train_from_file(sys.argv[1])

   numData = data.length_train_data()


   min_error = sys.float_info.max
   best_network = None
   firstTime = True


   for i in range(_MAX_NETS):
      current_error = sys.float_info.max
      best_test = sys.float_info.max
      best_train = sys.float_info.max

      # Create a network
      network = fann.neural_net()
      network.create_standard_array( (
            data.num_input_train_data(), 
            # hidden as 1/2 * inputs seems good rule-of-thumb
            data.num_input_train_data()/2, 
            data.num_output_train_data()) )

      # Set the training algorithm and hidden/output functions
      network.set_training_algorithm(_TRAIN_ALGORITHM)
      network.set_activation_function_hidden(_HIDDEN_FUNC)
      network.set_activation_function_output(_OUTPUT_FUNC)

      network.set_input_scaling_params(data, 0.0, 1.0)

      # Shuffle the data and split into training and validation sets
      data.shuffle_train_data()

      train_data = fann.training_data(data)
      train_data.subset_train_data(0, numData/2)
      network.scale_train(train_data)

      test_data = fann.training_data(data)
      test_data.subset_train_data(numData/2, numData/2)
      network.scale_train(test_data)


      # Initialize the network using the data
      network.init_weights(train_data)

      # Train!
      progress = True
      epochs = 0
      num_calls = 0
      while (progress):
          train_mse = network.train_epoch(train_data)
          # Test the ANN on the test data
          test_mse =  network.test_data(test_data)
          mse = train_mse + test_mse
          epochs+=1
          print epochs,'\t',num_calls, '\t',current_error, '\t',mse, '\t',test_mse, '\t',train_mse
          # If error is higher, quit, else keep going
          num_calls += 1
          if (( train_mse > best_train or test_mse > best_test) and (num_calls > _MAX_CALLS) ):
              num_calls = 0
              progress = False
          else:
              if (test_mse < best_test): best_test = test_mse
              if (train_mse < best_train): best_train = train_mse
              if (train_mse <= best_train and test_mse <= best_test ):
                  current_error = mse
                  num_calls = 0


      # See if this network has the best MSE
      if (current_error < min_error):
          # Save the network we've trained
          network.save(sys.argv[2])
          min_error = current_error
      print 'MSE: ',min_error,'\n'

      network.destroy()
      test_data.destroy_train()
      train_data.destroy_train()

   sys.exit(0)
