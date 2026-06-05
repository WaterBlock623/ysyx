ifeq ($(wildcard $(NPC_HOME)/espresso/espresso),)
$(info Init espresso...)
$(shell mkdir -p $(NPC_HOME)/espresso/)
$(shell wget https\://github.com/chipsalliance/espresso/releases/download/v2.4/x86_64-linux-gnu-espresso -O $(NPC_HOME)/espresso/espresso > /dev/null 2>&1)
$(shell chmod u+x $(NPC_HOME)/espresso/espresso)
endif

ifneq ($(GITHUB_PATH),)
$(info Found GITHUB_PATH: $(GITHUB_PATH))
ifeq ($(shell command -v espresso),)
# $(shell echo "$(NPC_HOME)/espresso" >> $(GITHUB_PATH))
$(shell cp $(NPC_HOME)/espresso/espresso /usr/local/bin/espresso)
endif
endif

ifeq ($(shell command -v espresso),) # Check
$(error Can not find espresso)
endif

ifeq ($(wildcard $(NPC_HOME)/sbt/bin/sbt),)
$(info Init sbt...)
$(shell wget -q -O - https\://github.com/sbt/sbt/releases/download/v1.12.11/sbt-1.12.11.tgz | tar -zxvf - > /dev/null 2>&1)
endif

ifeq ($(wildcard ~/.ivy2/local/cn.ac.ios.tis/riscvspeccore_2.13/1.3-chisel7.11.0-7e43b32-SNAPSHOT/jars/riscvspeccore_2.13.jar),)
$(info Init ChiRVFormal...)
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
