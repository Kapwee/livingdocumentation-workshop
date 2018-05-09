package flottio.livingdocumentation;

import static flottio.livingdocumentation.SimpleTemplate.evaluate;
import static flottio.livingdocumentation.SimpleTemplate.readTestResource;
import static flottio.livingdocumentation.SimpleTemplate.write;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static guru.nidi.graphviz.model.Factory.to;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotatedElement;

import flottio.annotations.BoundedContext;
import flottio.annotations.ExternalActor;
import flottio.annotations.ExternalActor.ActorType;
import guru.nidi.graphviz.attribute.Arrow;
import guru.nidi.graphviz.attribute.Attributes;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Font;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.attribute.RankDir;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.Node;
import guru.nidi.graphviz.model.Serializer;

/**
 * Living Diagram of the system and its external actors generated out of the
 * code thanks to the package naming conventions.
 */
public class SystemDiagramTest {

	private static final String SCM_BASE_URL = "https://github.com/cyriux/livingdocumentation-workshop/blob/master/living-documentation-workshop";
	private static final String PACKAGE_PREFIX = "flottio.fuelcardmonitoring";
	private static final String SOURCE_TREE = "src/main/java";

	@Test
	public void generateDiagram() throws Exception {
		final ClassPath classPath = ClassPath.from(Thread.currentThread().getContextClassLoader());

		final JavaProjectBuilder builder = new JavaProjectBuilder();
		// Adding all .java files in a source tree (recursively).
		builder.addSourceTree(new File(SOURCE_TREE));

		final String prefix = PACKAGE_PREFIX;
		final ImmutableSet<ClassInfo> allClasses = classPath.getTopLevelClassesRecursive(prefix);

		Graph graph = buildEmptyGraph();
		HashMap<String, Node> nodes = new HashMap<>();

		final String domainPackageName = prefix + "." + "domain";

		final ImmutableSet<ClassInfo> domain = classPath.getTopLevelClasses(domainPackageName);
		final Map<ClassInfo, BoundedContext> inventory = new HashMap<ClassPath.ClassInfo, BoundedContext>();
		for (ClassInfo ci : domain) {
			BoundedContext bc = findBoundedContext(ci);
			if (bc != null) {
				inventory.put(ci, bc);
			}
		}

		for (ClassInfo classInfo : inventory.keySet()) {
			final BoundedContext bc = inventory.get(classInfo);
			final String systemName = bc.name().trim() + " System";
			// final String systemPicture = firstImageIn(bc.links());
//			digraph.addNode("system").setLabel(wrap(systemName, 19)).setComment("the system under consideration")
//					.setOptions(STUB_NODE_OPTIONS);
			nodes.put("system", node("system").with(
					Label.of(wrap(systemName, 19)),
					Color.GREY.font(),
					Color.GREY
			));
			graph = graph.with(nodes.get("system"));

			final List<ClassInfo> infra = allClasses.stream().filter(notIn("domain")).collect(Collectors.toList());
			for(ClassInfo ci : infra) {
				graph = printActor(graph, nodes, ci, builder);
			}
		}

		// render into image
		final String template = readTestResource("viz-template.html");

		String title = "Context Diagram";
		final String content = fromGraphToDotString(graph);
		final String text = evaluate(template, title, content);
		write("", "context-diagram.html", text);
	}

	private String fromGraphToDotString(Graph graph) {
		return new Serializer((MutableGraph) graph).serialize();
	}
	
	private Graph buildEmptyGraph() {
		return graph()
				.directed()
				.graphAttr().with(
						RankDir.LEFT_TO_RIGHT,
						Font.name("Verdana"),
						Font.size(12)
					)
				.linkAttr().with(
						Font.name("Verdana"),
						Font.size(9),
						Arrow.VEE
					)
				.nodeAttr().with(
						Shape.RECTANGLE,
						Font.name("Verdana"),
						Font.size(9)
					);
	}

	private static String firstImageIn(String[] strings) {
		for (String link : strings) {
			if (link.toLowerCase().endsWith(".png")) {
				return link;
			}
		}
		return null;
	}

	private BoundedContext findBoundedContext(ClassInfo ci) {
		BoundedContext first = null;
		final BoundedContext[] bc = ci.load().getAnnotationsByType(BoundedContext.class);
		for (BoundedContext boundedContext : bc) {
			first = boundedContext;
		}
		return first;
	}

	private final static String wrap(String words, final int length) {
		final StringBuilder sb = new StringBuilder(words);
		int i = 0;
		while (i + length < sb.length() && (i = sb.lastIndexOf(" ", i + length)) != -1) {
			sb.replace(i, i + 1, "\n");
		}
		return sb.toString();
	}

	protected Graph printActor(Graph graph, HashMap<String, Node> nodes, ClassInfo ci, JavaProjectBuilder builder) {
		final String url = (SCM_BASE_URL + "/" + SOURCE_TREE + "/") + toPath(ci) + "\"";

		final ExternalActor[] actors = ci.load().getAnnotationsByType(ExternalActor.class);
		for (ExternalActor actor : actors) {
			nodes.put(ci.getName(), node(ci.getName()).with(
					Label.of(wrap(actor.name(), 19)), 
					Style.FILLED, 
					Color.AZURE2.fill(),
					Attributes.attr("URL", url)
			));
			graph  = graph.with(nodes.get(ci.getName()));

			final String label = getComment(ci, builder);
			switch (actor.direction()) {
			case API:
				graph = graph.with(nodes.get(ci.getName()).link(to(nodes.get("system")).with(Arrow.NONE, Label.of(label))));
				break;
			case SPI:
				graph = graph.with(nodes.get("system").link(to(nodes.get(ci.getName())).with(
						Style.DASHED, 
						Arrow.NONE, 
						Label.of(label),
						Attributes.attr("id", Math.random())
				)));
				break;
			default:
				graph = graph.with(nodes.get("system").link(to(nodes.get(ci.getName())).with(
						Style.DASHED, 
						Arrow.NONE, 
						Label.of(label),
						Attributes.attr("id", Math.random())
				)));
				graph = graph.with(nodes.get(ci.getName()).link(to(nodes.get("system")).with(Arrow.NONE, Label.of(label))));
			}
		}
		return graph;
	}

	public String toPath(ClassInfo ci) {
		return ci.getResourceName().replace(".class", ".java").replace("package-info.java", "");
	}

	private final static String actorType(ActorType type) {
		switch (type) {
		case PEOPLE:
			return "External User";
		case SYSTEM:
		default:
			return "External System";
		}
	}

	private String getComment(ClassInfo ci, JavaProjectBuilder builder) {
		JavaAnnotatedElement doc = ci.getSimpleName().equals("package-info")
				? builder.getPackageByName(ci.getPackageName()) : builder.getClassByName(ci.getName());
		final String label = doc.getComment() == null ? "" : wrap(doc.getComment(), 30);
		return label;
	}

	private Predicate<ClassInfo> notIn(final String packageName) {
		return new Predicate<ClassInfo>() {
			public boolean test(ClassInfo ci) {
				final boolean nameConvention = !ci.getSimpleName().endsWith("Test")
						&& !ci.getSimpleName().endsWith("IT") && !ci.getPackageName().endsWith("." + packageName);
				return nameConvention;
			}

		};
	}
}
