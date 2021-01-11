import fann2.libfann as fann
import copy
import sys


_NUM_ARGS = 3



if __name__ == '__main__':

   # Sufficient Arguments?
   if (len(sys.argv) != _NUM_ARGS):
      print 'Insufficient arguments\nUsage: ',sys.argv[0],' <training data file> <output network filename>\n'
      sys.exit(-1)


   # Read the training data file
   data = fann.training_data()
   data.read_train_from_file(sys.argv[1])

   # Create a network
   network = fann.neural_net()
   network.create_shortcut_array( (data.num_input_train_data(), 1) )

   network.set_input_scaling_params(data, 0.0, 1.0)

   # Shuffle the data 
   network.scale_train(data)

   network.cascadetrain_on_data(data, 50, 1, .0001)

   # Save the network we've trained
   network.save(sys.argv[2])

   sys.exit(0)
