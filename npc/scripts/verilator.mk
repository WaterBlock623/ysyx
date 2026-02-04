VERILATOR = verilator
GTKWAVE = gtkwave

OBJ_DIR = $(BUILD_DIR)/obj_dir
WAVE_DIR = $(BUILD_DIR)/wave
WAVE = $(WAVE_DIR)/sim.fst
$(shell mkdir -p $(OBJ_DIR))
$(shell mkdir -p $(WAVE_DIR))

VERILATOR_CFLAGS += -MMD --cc --build -j 16 \
				-O3 --x-assign fast --x-initial fast --noassert

ifeq ($(CONFIG_NPC_WAVE),y)
$(info WAVE is enable)
VERILATOR_CFLAGS += --trace-fst
endif

VSRCS = $(shell find $(abspath $(VSRC_DIR)) -name "*.sv" -o -name "*.v")
CSRCS = $(shell find $(abspath $(WORK_DIR)/csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
ARCHIVES = $(OBJ_DIR)/libV$(TOPNAME).a $(OBJ_DIR)/libverilated.a $(OBJ_DIR)/V$(TOPNAME)__ALL.a

# Menuconfig
GUEST_ISA ?= $(call remove_quote,$(CONFIG_ISA))
ENGINE ?= $(call remove_quote,$(CONFIG_ENGINE))
NAME = $(GUEST_ISA)-nemu-$(ENGINE)

# FILELIST_MK = $(shell find -L $(NEMU_HOME)/src/isa -name "filelist.mk")
# include $(FILELIST_MK)

ifneq ($(CONFIG_CC),)
CC = $(call remove_quote,$(CONFIG_CC))
endif
CFLAGS_BUILD += $(call remove_quote,$(CONFIG_CC_OPT))
CFLAGS_BUILD += $(if $(CONFIG_CC_LTO),-flto,)
CFLAGS_BUILD += $(if $(CONFIG_CC_DEBUG),-O0 -ggdb3,)
CFLAGS_BUILD += $(if $(CONFIG_CC_ASAN),-fsanitize=address,)
CFLAGS_BUILD += $(if $(CONFIG_CC_UBSAN),-fsanitize=undefined,)
CFLAGS_BUILD += $(if $(CONFIG_CC_LKSAN),-fsanitize=leak,)
CXXFLAGS += $(CFLAGS_BUILD) -D__GUEST_ISA__=$(GUEST_ISA)

INC_PATH := $(WORK_DIR)/csrc/$(GUEST_ISA)/include \
						$(WORK_DIR)/include $(NEMU_HOME)/include $(INC_PATH)
export ADD_INC_PATH := $(INC_PATH)
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -D__TOP_NAME__="\"V$(TOPNAME)\"" \
						-D__TOP_NAME_INCLUDE__="\\\"V$(TOPNAME).h\\\"" \
						-D__WAVE__=$(WAVE)

NEMU_MAKE_FLAGS += WORK_DIR="$(WORK_DIR)" \
									 ADD_ARCHIVES="$(ARCHIVES)" ADD_LIBS="-lz"

lint:
	-$(VERILATOR) -Wall --lint-only --top-module $(TOPNAME) $(VSRCS)

build_ar: verilog
	# Build archives
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE) \
		$(addprefix -CFLAGS , $(CXXFLAGS)) \
		--Mdir $(OBJ_DIR)

wave:
	$(GTKWAVE) $(WAVE)

.PHONY: lint build_ar run gdb wave
