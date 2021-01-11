/* Mic1.s */

/**************
 * David M. Hansen
 * 11/30/2013
 *
 * Modified:
 *    12/2/2014 by David M. Hansen
 *       Changed LV and located "locals" for main above code, below stack
 *
 ****************/


.extern fgetc
.extern fopen
.extern puts
.extern printf
.extern fclose
.global main

   

/* Assign the Mic1's registers to ARM registers */
mic1PC .req r2
mic1MBRU .req r3
mic1MBR .req r4
mic1SP .req r5
mic1LV .req r6
mic1CPP .req r7
mic1MDR .req r8
mic1MAR .req r9
mic1TOS .req r10
mic1OPC .req r11
mic1H .req r12


/* Define a couple of useful macros */
.macro _WR_ /* Write */
   /* Load address of memory and write value in MDR to memory[MAR] */
   ldr r1, =memory 
   str mic1MDR, [r1, +mic1MAR]
.endm

.macro _RD_ /* Read */
   /* Load address of memory and read value in memory[MAR] to MDR */
   ldr r1, =memory 
   ldr mic1MDR, [r1, +mic1MAR]
.endm

.macro _FETCH_
   ldr r1, =memory     /* fetch into MBR and MBRU*/
   ldrsb mic1MBR, [r1, +mic1PC] 
   ldrb mic1MBRU, [r1, +mic1PC] 
.endm

.macro _INC_PC_FETCH_ /* Inc PC, fetch */
   add mic1PC, mic1PC, #1 /* PC = PC+1 */
   _FETCH_
.endm


.macro debug_ptr reg:req
   push  { r0-r3, lr, r9-r12 }@ Push even num of registers for ARM
                              @ conventions, including r0-r3, r9-r12 to
                              @ maintain previous state as well as 'lr'

   ldr   r0, =numfmt          @ in data section < fmt_ptr: .asciz "%p\n" >
   mov   r1, \reg             @ r1 <- parameter register 'reg'
   bl    printf               @ print it

   pop   { r0-r3, lr, r9-r12 }
.endm

.data

.balign 4
return:
 .word 0

.balign 4
fileMode:
 .asciz "r"
 
.balign 4
errormsg:
 .asciz "Error opening file. Usage: readFile [filename]"

.balign 4
numfmt:
 .asciz "%d\n"
 
.balign 4
errfmt:
 .asciz "ERROR: opcode is %d\n"
 
.balign 4
memory:
 .skip 4096
 
 
.text


.func main
main:
   push {lr}            /* So we can bx lr at the very end */

                        /* r0 has the count of arguments */
                        /* if not 2, error!  */
   cmp r0, #2
   beq openfile         /* OK, open the file */
   ldr r0, =errormsg    /* Error. Print error message and exit */
   bl puts
   b end
   
openfile:
                        /* r1 is an array of pointers to args */
                        /* r1 + 4 is the first arg */
   ldr r0, [r1, #4]     /* The filename needs to be arg0 */

   ldr r1, =fileMode    /* Open the file with read only */
   
   bl fopen             /* Call fopen */
   
   mov r3, r0         /* Save the address of the file */
   
   cmp r0, #0           /* Compare file pointer to null */
   bne init             /* If the file is not null, go to init */
   
   ldr r0, =errormsg    /* Print out an error message */
   bl puts
   b end 
 

init:
   mov r0, r3          /* Load the address of file pointer */
   bl fgetc             /* Call fgetc */
   mov mic1LV, r0       /* store the byte in r0 */
   mov r0, r3          /* Load the address to file pointer */
   bl fgetc             /* Call fgetc */
   orr mic1LV, r0, mic1LV, LSL #8  /* shift LV 8 left and OR with r0 */

   /* At this point LV holds the number of words needed. We'll eventually 
      locate "local variables" above code and below the stack...
   */



   /* Fill memory with code using SP as the pointer */
   mov mic1PC, #0       /* PC starts at 0 */
   mov mic1SP, mic1PC   /* Init SP to PC to start with, inc as we load */

   ldr r11, =memory     /* Cache memory address in r11 */
loop:
   mov r0, r3        /* Load the address of file pointer */
   
   bl fgetc             /* Call fgetc */
   mov r1, r11          /* Memory address in r1 */
   strb r0, [r1, +mic1SP]  /* Store a byte in the program */
   add mic1SP, mic1SP, #1
   cmp r0, #-1          /* Compare to -1 (EOF) */
   
   bne loop             /* If not EOF, branch to loop */

   mov r0, r3          /* Close the file */
   bl fclose


   /* PC is 0, LV holds #locals; Set up SP, and LV on word-aligned * boundary to be safe */
   mov r0, mic1SP       /* Word align SP and setup rest based on that location */
   and r0, #3           /* get SP % 4 subtract that from 4 and add to SP to align */
   mov r1, #4
   sub r0, r1, r0
   add mic1SP, mic1SP, r0  /* Align SP */
   mov r1, mic1SP                      /* SP is now at start of "locals", remember it */
   add mic1SP, mic1SP, mic1LV, LSL #2  /* Make space for locals by adding LV*4 to SP */
   mov mic1LV, r1                      /* Set LV to point to proper location (where SP was) */




/*** 
   Memory initialized:
   PC points to first instruction @0 (load below)
   LV points to start of locals (word aligned)
   SP points to top of stack (word aligned; TOS is empty)
***/

   mov r1, r11 /* Load the first instruction into the MBR (r11 is cached memory address) */
   ldrsb mic1MBR, [r1, +mic1PC] 
   ldrb mic1MBRU, [r1, +mic1PC] 



main1:   /* Start of the Mic1 main loop */
   mov r0, mic1MBRU         /* Remember the value of the MBR */
   _INC_PC_FETCH_
   /********* Goto the instruction ***********/
   cmp r0, #0x60
   beq iadd
   cmp r0, #0x64
   beq isub
   cmp r0, #0x7E
   beq iand
   cmp r0, #0x80
   beq ior
   cmp r0, #0x59
   beq dup
   cmp r0, #0x57
   beq pop
   cmp r0, #0x5F
   beq swap
   cmp r0, #0x10
   beq bipush
   cmp r0, #0x15
   beq iload
   cmp r0, #0x36
   beq istore
   cmp r0, #0x84
   beq iinc
   cmp r0, #0xA7
   beq goto
   cmp r0, #0x6C
   beq idiv
   cmp r0, #0x68
   beq imul
   cmp r0, #0x9B
   beq iflt
   cmp r0, #0x99
   beq ifeq
   cmp r0, #0x9F
   beq if_icmpeq
   cmp r0, #0xA8
   beq jsr
   cmp r0, #0xA9
   beq ret
   b error            /* default - quit! */ 


iadd:
   sub mic1SP, mic1SP, #4       /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS           /* H = TOS */
   _RD_
   add mic1TOS, mic1MDR, mic1H  /* TOS = MDR + H */
   mov mic1MDR, mic1TOS         /* MDR = TOS */
   _WR_
   b main1


isub:
   sub mic1SP, mic1SP, #4       /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS           /* H = TOS */
   _RD_
   sub mic1TOS, mic1MDR, mic1H  /* TOS = MDR - H */
   mov mic1MDR, mic1TOS         /* MDR = TOS */
   _WR_
   b main1


iand:
   sub mic1SP, mic1SP, #4        /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS            /* H = TOS */
   _RD_
   and mic1TOS, mic1MDR, mic1H   /* TOS = MDR & H */
   mov mic1MDR, mic1TOS          /* MDR = TOS */
   _WR_
   b main1


ior:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS      /* H = TOS */
   _RD_
   orr mic1TOS, mic1MDR, mic1H  /* TOS = MDR | H */
   mov mic1MDR, mic1TOS         /* MDR = TOS */
   _WR_
   b main1


dup:
   add mic1SP, mic1SP, #4 /* MAR = SP = SP+1 */
   mov mic1MAR, mic1SP
   mov mic1MDR, mic1TOS     /* MDR = TOS */
   _WR_
   b main1

   
pop:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   _RD_
   mov mic1TOS, mic1MDR /* TOS = MDR */
   b main1


swap:
   sub mic1MAR, mic1SP, #4 /* MAR = SP-1 */
   _RD_
   mov mic1MAR, mic1SP     /* MAR = SP */
   mov mic1H, mic1MDR     /* H = MDR */
   _WR_
   mov mic1MDR, mic1TOS /* MDR = TOS */
   sub mic1MAR, mic1SP, #4 /* MAR = SP-1 */
   _WR_
   mov mic1TOS, mic1H /* TOS = H */
   b main1


bipush:
   add mic1SP, mic1SP, #4 /* MAR = SP = SP+1 */
   mov mic1MAR, mic1SP
   mov mic1TOS, mic1MBR    /* MDR = TOS = MBR */
   mov mic1MDR, mic1TOS
   _WR_
   _INC_PC_FETCH_
   b main1


iload:
   mov mic1H, mic1LV    /* H = LV */
   add mic1MAR, mic1H, mic1MBRU, LSL #2 /* MAR = MBRU + H */ 
   _RD_
   add mic1SP, mic1SP, #4  /* MAR = SP = SP+1 */
   mov mic1MAR, mic1SP
   _INC_PC_FETCH_
   _WR_
   mov mic1TOS, mic1MDR    /* TOS = MDR */
   b main1
  

istore:
   mov mic1H, mic1LV    /* H = LV */
   add mic1MAR, mic1H, mic1MBRU, LSL #2 /* MAR = MBRU + H */ 
   mov mic1MDR, mic1TOS    /* MDR = TOS */
   _WR_
   sub mic1SP, mic1SP, #4  /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   _RD_
   _INC_PC_FETCH_
   mov mic1TOS, mic1MDR    /* TOS = MDR */
   b main1
  

iinc:
   mov mic1H, mic1LV          /* H = LV */
   add mic1MAR, mic1H, mic1MBRU, LSL #2 /* MAR = H + MBRU*4 */
   _RD_
   _INC_PC_FETCH_
   mov mic1H, mic1MDR         /* H = MDR */
   mov r0, mic1MBR
   _INC_PC_FETCH_
   add mic1MDR, mic1H , r0    /* MDR = H + MBR*/
   _WR_
   b main1
  

goto:
   sub mic1OPC, mic1PC, #1   /* OPC = PC-1 */
goto2:
   mov mic1H, mic1MBR, LSL #8   /* H = MBR << 8 */
   _INC_PC_FETCH_
   orr mic1H, mic1MBRU, mic1H       /* H = MBRU | H */
   add mic1PC, mic1OPC, mic1H /* PC = OPC + H */
   _FETCH_
   b main1

ret:
   cmp mic1CPP, #0    /* If CPP holds 0, exit */
   beq end
   mov mic1MAR, mic1CPP /* MAR = CPP; rd */
   _RD_
   mov mic1CPP, mic1MDR /* CPP = MDR */
   add mic1MAR, mic1MAR, #4   /* MAR = MAR + 1; rd */
   _RD_
   mov mic1PC, mic1MDR        /* PC = MDR; fetch */
   _FETCH_
   add mic1MAR, mic1MAR, #4   /* MAR = MAR + 1; rd */
   _RD_
   mov mic1MAR, mic1LV         /* SP = MAR = LV */
   mov mic1SP, mic1MAR
   mov mic1LV, mic1MDR        /* LV = MDR */
   mov mic1MDR, mic1TOS       /* MDR = TOS; wr */
   _WR_
   b main1 

imul:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS      /* H = TOS */
   _RD_
   mul mic1TOS, mic1MDR, mic1H  /* TOS = MDR * H */
   mov mic1MDR, mic1TOS         /* MDR = TOS */
   _WR_
   b main1


idiv:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP-1 */
   mov mic1MAR, mic1SP
   mov mic1H, mic1TOS      /* H = TOS */
   _RD_
   mov r0, mic1MDR
   mov r1, mic1H
   bl signed_divide /* TOS = MDR / H */
   mov mic1TOS, r0
   mov mic1MDR, mic1TOS         /* MDR = TOS */
   _WR_
   b main1


iflt:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP - 1 */
   mov mic1MAR, mic1SP
   _RD_
   mov mic1OPC, mic1TOS    /* OPC = TOS */
   mov mic1TOS, mic1MDR    /* TOS = MDR */
   cmp mic1OPC, #0
   blt true
   b false

ifeq:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP - 1 */
   mov mic1MAR, mic1SP
   _RD_
   mov mic1OPC, mic1TOS    /* OPC = TOS */
   mov mic1TOS, mic1MDR    /* TOS = MDR */
   cmp mic1OPC, #0
   beq true
   b false

if_icmpeq:
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP - 1 */
   mov mic1MAR, mic1SP
   _RD_
   mov mic1H, mic1MDR      /* H = MDR */
   sub mic1SP, mic1SP, #4 /* MAR = SP = SP - 1 */
   mov mic1MAR, mic1SP
   _RD_
   mov mic1OPC, mic1TOS    /* OPC = TOS */
   mov mic1TOS, mic1MDR    /* TOS = MDR */
   cmp mic1OPC, mic1H      /* if equal, T, else F */
   beq true
   b false


jsr:
   add mic1SP, mic1SP, #4              /* SP = SP + MBR + 1 */
   add mic1SP, mic1SP, mic1MBR, LSL #2 
   mov mic1MDR, mic1CPP                /* MDR = CPP */
   mov mic1CPP, mic1SP                 /* MAR = CPP = SP; wr */ 
   mov mic1MAR, mic1CPP
   _WR_
   add mic1MDR, mic1PC, #4             /* MDR = PC + 4 */
   add mic1SP, mic1SP, #4              /* MAR = SP = SP + 1; wr */
   mov mic1MAR, mic1SP
   _WR_
   mov mic1MDR, mic1LV                 /* MDR = LV */
   add mic1SP, mic1SP, #4              /* MAR = SP = SP + 1; wr */
   mov mic1MAR, mic1SP
   _WR_
   sub mic1LV, mic1SP, #8              /* LV = SP - 2 - MBR */
   sub mic1LV, mic1MBR, LSL #2
   _INC_PC_FETCH_                      /* get # args */
   sub mic1LV, mic1LV, mic1MBR, LSL #2 /* LV = LV - MBR */
   _INC_PC_FETCH_                      /* get high byte of addr */
   mov mic1H, mic1MBR, LSL #8          /* H = MBR << 8 */
   _INC_PC_FETCH_                      /* get high byte of addr */
   orr mic1H, mic1H, mic1MBRU           /* PC = PC - 4 + (H OR MBRU); fetch */
   sub mic1PC, mic1PC, #4
   add mic1PC, mic1PC, mic1H
   _FETCH_
   sub mic1CPP, mic1SP, #8             /* CPP = SP - 2 */
   b main1                         

true:
   b goto

false:
   add mic1PC, mic1PC, #1  /* PC = PC + 1 */
   _INC_PC_FETCH_
   b main1

end:
   ldr r0, =numfmt
   mov r1, mic1TOS
   bl printf               /* Call printf */

   mov r0, #0              /* Exit with a success code */
   pop {lr} 

   bx lr                   /* Return */



/* r0 holds a bad opcode */
error: 
   mov r1, r0
   ldr r0, =errfmt
   bl printf               /* Call printf */

   pop {lr}  
   bx lr                   /* Return */



/* 
 * Courtesy of 
 * http://thinkingeek.com/2013/08/11/arm-assembler-raspberry-pi-chapter-15/
 */
signed_divide:
    push {r2, r3, r4, lr}
    mov r4, #0               /* assume sign is positive */
    cmp r0, #0
    bge .Lpos                 /* Is r0 positive? */
    mvn r0, r0                /* r0 is negtive */
    add r0, r0, #1
    mov r4, #1               /* negative result */
    cmp r1, #0                /* Is r1 negative? */
    bge .Lcompute             /* No, compute */
    mvn r1, r1                /* Yes, negate and positive answer */
    add r1, r1, #1
    mov r4, #1
    b .Lcompute
    .Lpos:                     /* r0 is positive */
    cmp r1, #0                 /* Is r1 positive? */
    bge .Lcompute              /* Yes, compute */
    mvn r1, r1                 /* no, negative result, compute */
    add r1, r1, #1
    mov r4, #1

    .Lcompute:
    clz  r3, r0                /* r3 ← CLZ(r0) Count leading zeroes of N */
    clz  r2, r1                /* r2 ← CLZ(r1) Count leading zeroes of D */
    sub  r3, r2, r3            /* r3 ← r2 - r3. 
                                 This is the difference of zeroes
                                 between D and N. 
                                 Note that N >= D implies CLZ(N) <= CLZ(D)*/
    add r3, r3, #1             /* Loop below needs an extra iteration count */
 
    mov r2, #0                 /* r2 ← 0 */
    b .Lloop_check4
    .Lloop4:
      cmp r0, r1, lsl r3       /* Compute r0 - (r1 << r3) and update cpsr */
      adc r2, r2, r2           /* r2 ← r2 + r2 + C.
                                  Note that if r0 >= (r1 << r3) then C=1, C=0 otherwise */
      subcs r0, r0, r1, lsl r3 /* r0 ← r0 - (r1 << r3) if C = 1 (this is, only if r0 >= (r1 << r3) ) */
    .Lloop_check4:
      subs r3, r3, #1          /* r3 ← r3 - 1 */
      bpl .Lloop4              /* if r3 >= 0 (N=0) then branch to .Lloop1 */
 
    cmp r4, #0
    beq .Ldone
    mvn r2, r2                /* Answer is negative */
    add r2, r2, #1

    .Ldone:
    mov r0, r2
    pop {r2, r3, r4, lr}    
    bx lr

.endfunc
