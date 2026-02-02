include $(NEMU_HOME)/scripts/config.mk

WORK_DIR = $(shell pwd)

run:
	$(MAKE) -C $(NEMU_HOME) run WORK_DIR=$(WORK_DIR) ADD_ARCHIVES=$(ARCHIVE)

PRJ = playground
PACKAGE_NAME = sirius
BUILD_DIR = $(WORK_DIR)/build
MILL = ./mill

ELABORATE_SCALA = ./playground/src/$(PACKAGE_NAME)/Elaborate.scala
EXT_TOP_CMD = grep "emitSystemVerilogFile" $(ELABORATE_SCALA) | \
			  grep "new " | \
			  sed -E 's/.*new +([^ (,]+).*/\1/' | \
			  sed 's/.*\.//'
TOPNAME = $(strip $(shell $(EXT_TOP_CMD)))
NXDC_FILES = constr/$(TOPNAME).nxdc

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --cc --build \
				-O3 --x-assign fast --x-initial fast --noassert
# #				--trace-fst
#
GTKWAVE = gtkwave
#
OBJ_DIR = $(BUILD_DIR)/obj_dir
WAVE_DIR = $(OBJ_DIR)/wave
WAVE = $(WAVE_DIR)/sim.fst
# BIN = $(BUILD_DIR)/$(TOPNAME)
ARCHIVE = $(OBJ_DIR)/libV$(TOPNAME).a
$(info $(ARCHIVE))


$(shell mkdir -p $(BUILD_DIR))

# # constraint file
# SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
# $(SRC_AUTO_BIND): $(NXDC_FILES)
# 	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@
#
# test_path:
# 	@echo "[$(NXDC_FILES)]"
# 	@echo "[$(TOPNAME)]"
#
# # project source
VSRCS = $(shell find $(abspath $(BUILD_DIR)) -name "*.sv" -o -name "*.v")
CSRCS = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
# if ($(NVBOARD))
# 	CSRCS += $(SRC_AUTO_BIND)
# endif

#
# # rules for NVBoard
# include $(NVBOARD_HOME)/scripts/nvboard.mk
# ifeq ($(NVBOARD), off)
# 	NVBOARD_ARCHIVE := 
# 	CXXFLAGS += -DNO_NVBOARD
# endif
#
# # rules for verilator


-include $(WORK_DIR)/include/config/auto.conf
-include $(WORK_DIR)/include/config/auto.conf.cmd

remove_quote = $(patsubst "%",%,$(1))

# Extract variabls from menuconfig
GUEST_ISA ?= $(call remove_quote,$(CONFIG_ISA))
ENGINE ?= $(call remove_quote,$(CONFIG_ENGINE))
NAME    = $(GUEST_ISA)-nemu-$(ENGINE)

# Include all filelist.mk to merge file lists
FILELIST_MK = $(shell find -L $(NEMU_HOME)/src/isa -name "filelist.mk")
include $(FILELIST_MK)

# Extract compiler and options from menuconfig
ifneq ($(CONFIG_CC),)
CC = $(call remove_quote,$(CONFIG_CC))
endif
CFLAGS_BUILD += $(call remove_quote,$(CONFIG_CC_OPT))
CFLAGS_BUILD += $(if $(CONFIG_CC_LTO),-flto,)
CFLAGS_BUILD += $(if $(CONFIG_CC_DEBUG),-O0 -ggdb3,)
CFLAGS_BUILD += $(if $(CONFIG_CC_ASAN),-fsanitize=address,)
CFLAGS_BUILD += $(if $(CONFIG_CC_UBSAN),-fsanitize=undefined,)
CFLAGS_BUILD += $(if $(CONFIG_CC_LKSAN),-fsanitize=leak,)
CXXFLAGS  += $(CFLAGS_BUILD) $(CFLAGS_TRACE) -D__GUEST_ISA__=$(GUEST_ISA)

INC_PATH := $(WORK_DIR)/csrc/riscv32_npc/include \
						$(WORK_DIR)/include $(NEMU_HOME)/include $(INC_PATH)
$(info $(INC_PATH))
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -D__TOP_NAME__="\"V$(TOPNAME)\"" -include V$(TOPNAME).h# -O0 -ggdb3
#
# # am
# ARGS ?=
# IMG ?=
#
# test:
# 	$(MILL) -i $(PRJ).test
#
verilog:
	$(call git_commit, "generate verilog")
	# mkdir -p $(BUILD_DIR)
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --target-dir $(BUILD_DIR)
	-$(MAKE) lint
#
# help:
# 	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --help
#
# reformat:
# 	$(MILL) -i __.reformat
#
# checkformat:
# 	$(MILL) -i __.checkFormat
#
# bsp:
# 	$(MILL) -i mill.bsp.BSP/install
#
# idea:
# 	$(MILL) -i mill.idea.GenIdea/idea
#
# clean:
# 	-rm -rf $(BUILD_DIR)
#
# .PHONY: test verilog help reformat checkformat clean
#
# sim: verilog lint $(BIN)
# 	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
# 	@$(BIN) $(IMG) $(ARGS)
#
# simbatch: $(BIN)
# #	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
# 	@$(BIN) $(IMG) $(ARGS)
#
# simwave: VERILATOR_CFLAGS += --trace-fst
# simwave: CXXFLAGS += -DENAWAVE
# simwave: sim
#
test: $(ARCHIVE)

$(ARCHIVE): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	# @mkdir -p $(WAVE_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) \
		--Mdir $(OBJ_DIR) 
	# -o $(abspath $(ARCHIVE))

# $(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
# 	@rm -rf $(OBJ_DIR)
# 	@mkdir -p $(WAVE_DIR)
# 	$(VERILATOR) $(VERILATOR_CFLAGS) \
# 		--top-module $(TOPNAME) $^ \
# 		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
# 		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))
#
# wave:
# 	$(GTKWAVE) $(WAVE)
#
lint:
	-$(VERILATOR) -Wall --lint-only --top-module $(TOPNAME) $(VSRCS)
#
# .PHONY: wave lint sim
#
include ../Makefile
