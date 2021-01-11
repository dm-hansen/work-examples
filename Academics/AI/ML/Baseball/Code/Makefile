GCC=gcc

TARGETS = train predict 

all: $(TARGETS)

%: %.c Makefile
	$(GCC) -O3 $< -o $@ -lfann -lm

clean:
	rm -f $(TARGETS) 
