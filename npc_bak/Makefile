TOPNAME = top
NXDC_FILES = constr/top.nxdc
INC_PATH ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert \
				--trace-fst

GTKWAVE = gtkwave

BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
WAVE_DIR = $(OBJ_DIR)/wave
WAVE = $(WAVE_DIR)/sim.fst
BIN = $(BUILD_DIR)/$(TOPNAME)

default: all 

$(shell mkdir -p $(BUILD_DIR))

# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# project source
VSRCS = $(shell find $(abspath ./vsrc) -name "*.v")
CSRCS = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
CSRCS += $(SRC_AUTO_BIND)

# rules for NVBoard
include $(NVBOARD_HOME)/scripts/nvboard.mk

# rules for verilator
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""

all: commit lint $(BIN)

$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	@mkdir -p $(WAVE_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

run: all
	@$(BIN)

clean:
	rm -rf $(BUILD_DIR)

wave:
	$(GTKWAVE) $(WAVE)

commit:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!

lint:
	$(VERILATOR) -Wall --lint-only $(VSRCS)

.PHONY: default all clean run wave commit lint

include ../Makefile
