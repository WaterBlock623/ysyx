BUILD_DIR = ./build
PRJ = playground
MILL = ./mill

ELABORATE_SCALA = ./playground/src/Elaborate.scala
EXT_TOP_CMD = grep "emitSystemVerilogFile" $(ELABORATE_SCALA) | \
			  grep "new " | \
			  sed -E 's/.*new +([^ (,]+).*/\1/' | \
			  sed 's/.*\.//'
TOPNAME := $(strip $(shell $(EXT_TOP_CMD)))
NXDC_FILES = constr/$(TOPNAME).nxdc
INC_PATH ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert
#				--trace-fst

GTKWAVE = gtkwave

OBJ_DIR = $(BUILD_DIR)/obj_dir
WAVE_DIR = $(OBJ_DIR)/wave
WAVE = $(WAVE_DIR)/sim.fst
BIN = $(BUILD_DIR)/$(TOPNAME)


$(shell mkdir -p $(BUILD_DIR))

# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

test_path:
	@echo "[$(NXDC_FILES)]"
	@echo "[$(TOPNAME)]"

# project source
VSRCS = $(shell find $(abspath $(BUILD_DIR)) -name "*.sv" -o -name "*.v")
CSRCS = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
ifneq ($(NVBOARD), off)
	CSRCS += $(SRC_AUTO_BIND)
endif

# rules for NVBoard
include $(NVBOARD_HOME)/scripts/nvboard.mk
ifeq ($(NVBOARD), off)
	NVBOARD_ARCHIVE := 
	CXXFLAGS += -DNO_NVBOARD
endif

# rules for verilator
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\"" -include V$(TOPNAME).h

GENERATOR = minirvcpu.util.GenCtrlSignals

test:
	$(MILL) -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	$(MILL) -i $(PRJ).runMain Elaborate --target-dir $(BUILD_DIR)
	-$(MAKE) lint

help:
	$(MILL) -i $(PRJ).runMain Elaborate --help

reformat:
	$(MILL) -i __.reformat

checkformat:
	$(MILL) -i __.checkFormat

bsp:
	$(MILL) -i mill.bsp.BSP/install

idea:
	$(MILL) -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help reformat checkformat clean

sim: lint $(BIN)
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@$(BIN)

simwave: VERILATOR_CFLAGS += --trace-fst
simwave: CXXFLAGS += -DENAWAVE
simwave: sim

$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	@mkdir -p $(WAVE_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

wave:
	$(GTKWAVE) $(WAVE)

lint:
	-$(VERILATOR) -Wall --lint-only --top-module $(TOPNAME) $(VSRCS)

.PHONY: wave lint sim

include ../Makefile
