ifeq ($(wildcard $(NPC_HOME)/ChiRVFormal/.git),)
	$(info Init ChiRVFormal...)
  $(shell git submodule update --init $(NPC_HOME)/ChiRVFormal)
  $(shell cd $(NPC_HOME)/ChiRVFormal; \
		sbt publishLocal -DHashId=true -DChiselVersion=7.11.0 -DScalaVersion=2.13.18)
endif

ifeq ($(wildcard $(NPC_HOME)/rvdecoderdb/.git),)
	$(info Init rvdecoderdb...)
  $(shell git submodule update --init $(NPC_HOME)/rvdecoderdb)
endif

ifeq ($(wildcard $(NPC_HOME)/riscv-opcodes/.git),)
	$(info Init riscv-opcodes...)
  $(shell git submodule update --init $(NPC_HOME)/riscv-opcodes)
endif
