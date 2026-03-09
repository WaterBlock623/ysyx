_STATIC_LIBS = libgdbstub.a
STATIC_LIBS = $(addprefix $(BUILD_DIR)/,$(_STATIC_LIBS))

TOOLS_DIR = $(NEMU_HOME)/tools

mini-gdbstub:
	$(MAKE) -C $(TOOLS_DIR)/mini-gdbstub all O=$(BUILD_DIR) LIBGDBSTUB=$(BUILD_DIR)/libgdbstub.a

$(STATIC_LIBS): mini-gdbstub

.PHONY: mini-gdbstub $(STATIC_LIBS)
