package works.earendil.pi.codingagent.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;

@Command(name = "pi", mixinStandardHelpOptions = true, version = "0.80.2",
        description = "Pi Coding Agent CLI - Java Edition")
public class CliArgs {

    @Option(names = {"--provider"}, description = "Model provider ID (e.g. openai, anthropic)")
    public String provider;

    @Option(names = {"--model"}, description = "Model ID to use")
    public String model;

    @Option(names = {"--api-key"}, description = "API key for the provider")
    public String apiKey;

    @Option(names = {"--thinking"}, description = "Thinking level: off, minimal, low, medium, high, xhigh")
    public String thinking;

    @Option(names = {"-p", "--print"}, description = "Print mode: single-shot prompt and output to stdout")
    public boolean print;

    @Option(names = {"--mode"}, description = "Output mode for print: text or json", defaultValue = "text")
    public String mode = "text";

    @Option(names = {"--session"}, description = "Resume or switch to session path")
    public String session;

    @Option(names = {"--no-tools"}, description = "Disable all tools")
    public boolean noTools;

    @Option(names = {"--tools"}, split = ",", description = "Comma-separated list of tools to enable")
    public List<String> tools = new ArrayList<>();

    @Option(names = {"--list-models"}, description = "List available models and exit")
    public boolean listModels;

    @Parameters(description = "Messages or prompts to send to the agent")
    public List<String> messages = new ArrayList<>();
}
