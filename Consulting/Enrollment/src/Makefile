# This makefile is on purpose not made with configure, to show how to use the library
# The make file requires that the fann library is installed (see ../README)

GCC=gcc

TARGETS = gfu_net_train gfu_predict gfu_net_train_early_stop

all: $(TARGETS)

%: %.c Makefile
	$(GCC) -O3 $< -o $@ -ldoublefann -lm

%_fixed: %.c Makefile
	$(GCC) -O3 -DFIXEDFANN $< -o $@ -lfixedfann -lm

clean:
	rm -f $(TARGETS) 
