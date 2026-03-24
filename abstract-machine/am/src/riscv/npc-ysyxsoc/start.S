.section entry, "ax"
.globl _start
.type _start, @function
.option norelax

_start:
  la gp, __global_pointer$
  la sp, _stack_pointer
  call _trm_init

.size _start, . - _start
