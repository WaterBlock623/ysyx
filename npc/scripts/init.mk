ifeq ($(wildcard $(NPC_HOME)/ChiRVFormal/.git),)
$(info Init ChiRVFormal...)
$(shell wget -q -O - https\://github.com/sbt/sbt/releases/download/v1.12.11/sbt-1.12.11.tgz | tar -zxvf - > /dev/null 2>&1)
$(shell git submodule update --init $(NPC_HOME)/ChiRVFormal)
$(shell cd $(NPC_HOME)/ChiRVFormal; ../sbt/bin/sbt publishLocal -DHashId=true -DChiselVersion=7.11.0 -DScalaVersion=2.13.18 > /dev/null 2>&1)
endif

ifeq ($(wildcard $(NPC_HOME)/rvdecoderdb/.git),)
$(info Init rvdecoderdb...)
$(shell git submodule update --init $(NPC_HOME)/rvdecoderdb)
endif

ifeq ($(wildcard $(NPC_HOME)/riscv-opcodes/.git),)
$(info Init riscv-opcodes...)
$(shell git submodule update --init $(NPC_HOME)/riscv-opcodes)
endif
