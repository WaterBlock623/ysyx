ifeq ($(wildcard $(NEMU_HOME)/tools/mini-gdbstub/.git),)
  $(shell git submodule update --init $(NEMU_HOME)/tools/mini-gdbstub)
endif
