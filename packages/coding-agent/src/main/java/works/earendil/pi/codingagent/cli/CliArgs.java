package works.earendil.pi.codingagent.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import works.earendil.pi.ai.model.Content;

import java.util.ArrayList;
import java.util.List;

@Command(name = "pi", mixinStandardHelpOptions = true, version = "0.81.1",
        description = "Pi Coding Agent CLI - Java Edition")
public class CliArgs {

    @Option(names = {"--provider"}, description = "Model provider ID (e.g. openai, anthropic)")
    public String provider;

    @Option(names = {"--model"}, description = "Model ID to use")
    public String model;

    @Option(names = {"--api-key"}, description = "API key for the provider")
    public String apiKey;

    @Option(names = {"--thinking"}, description = "Thinking level: off, minimal, low, medium, high, xhigh, max")
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

    @Option(names = {"-c", "--continue"}, description = "Continue previous session")
    public boolean continueSession;

    @Option(names = {"-r", "--resume"}, description = "Select a session to resume")
    public boolean resume;

    @Option(names = {"-n", "--name"}, description = "Set session display name")
    public String name;

    @Option(names = {"--no-session"}, description = "Don't save session (ephemeral)")
    public boolean noSession;

    @Option(names = {"--session-id"}, description = "Use exact project session ID")
    public String sessionId;

    @Option(names = {"--session-dir"}, description = "Directory for session storage and lookup")
    public String sessionDir;

    @Option(names = {"--fork"}, description = "Fork specific session into a new session")
    public String fork;

    @Option(names = {"--export"}, description = "Export session file to HTML or JSONL and exit")
    public String export;

    @Option(names = {"--offline"}, description = "Disable startup network operations")
    public boolean offline;

    @Option(names = {"--verbose"}, description = "Force verbose startup logs")
    public boolean verbose;

    @Option(names = {"-e", "--extension"}, split = ",", description = "Load an extension jar or directory")
    public List<String> extensions = new ArrayList<>();

    @Option(names = {"-ne", "--no-extensions"}, description = "Disable extension discovery")
    public boolean noExtensions;

    @Option(names = {"--skill"}, split = ",", description = "Load a skill file or directory")
    public List<String> skills = new ArrayList<>();

    @Option(names = {"--prompt-template"}, split = ",", description = "Load a prompt template")
    public List<String> promptTemplates = new ArrayList<>();

    @Option(names = {"--theme"}, split = ",", description = "Load a theme file")
    public List<String> themes = new ArrayList<>();

    @Parameters(description = "Messages or prompts to send to the agent")
    public List<String> messages = new ArrayList<>();

    public List<Content.Image> initialImages = new ArrayList<>();
}
