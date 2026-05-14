IVERILOG_HEX = $(BUILD_DIR)/$(notdir $(basename $(IMG)))_iverilog.hex

$(IVERILOG_HEX):
	mkdir -p $(dir $@)
	objcopy -I binary -O verilog $(IMG) $@

sim-iverilog: $(IVERILOG_HEX)


sim-iverilog-netlist: $(IVERILOG_HEX)
