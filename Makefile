# AI-assisted with OpenAI GPT-5 Codex.

COBC ?= cobc
BUILD_DIR ?= build
TARGET ?= $(BUILD_DIR)/maifetch

.PHONY: all clean test

all: $(TARGET)

$(TARGET): src/maifetch.cob
	mkdir -p $(BUILD_DIR)
	$(COBC) -x -free -Wall -o $(TARGET) src/maifetch.cob

test: $(TARGET)
	sh tests/run-fixture-test.sh $(TARGET)

clean:
	rm -rf $(BUILD_DIR)
