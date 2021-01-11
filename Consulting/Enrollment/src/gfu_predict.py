import fann2.libfann as fann
import sys


_NUM_ARGS = 3

'''
print "BUGGER - does not scale input"
sys.exit(-1)
'''

if __name__=='__main__':
    # Sufficient Arguments?
    if (len(sys.argv) != _NUM_ARGS):
        print 'Insufficient arguments\nUsage: ',sys.argv[0],' <network file> <data file>\n'
        sys.exit(-1)

    network = fann.neural_net()
    network.create_from_file(sys.argv[1])

    # Open and read the file of data to predict
    data = fann.training_data()
    data.read_train_from_file(sys.argv[2])
    network.scale_train(data)

    # Iterate over every entry in the datafile and make a prediction
    for anInput in data.get_input():
        print network.run(anInput)[0]
