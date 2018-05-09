package flottio.livingdocumentation;

import static flottio.livingdocumentation.SimpleTemplate.evaluate;
import static flottio.livingdocumentation.SimpleTemplate.readTestResource;
import static flottio.livingdocumentation.SimpleTemplate.write;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static guru.nidi.graphviz.model.Factory.to;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import guru.nidi.graphviz.attribute.Arrow;
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
 * Living Diagram of the Hexagonal Architecture generated out of the code thanks
 * to the package naming conventions.
 */
public class LivingDiagramTest {

	@Test
	public void generateDiagram() throws Exception {
		final ClassPath classPath = ClassPath.from(Thread.currentThread().getContextClassLoader());

		final String prefix = "flottio.fuelcardmonitoring";
		final ImmutableSet<ClassInfo> allClasses = classPath.getTopLevelClassesRecursive(prefix);

		Graph graph = buildEmptyGraph();
		Graph hexagon = graph().cluster().graphAttr().with(Label.of("Core Domain"));
		HashMap<String, Node> nodes = new HashMap<>();

		List<ClassInfo> domain = allClasses.stream().filter(filter(prefix, "domain")).collect(Collectors.toList());

		// add all domain model elements first
		for(ClassInfo ci : domain) {
			final Class clazz = ci.load();
			nodes.put(clazz.getName(), node(clazz.getSimpleName()));
			hexagon = hexagon.with(nodes.get(clazz.getName()));
		}
		graph = graph.with(hexagon);

		List<ClassInfo> infra = allClasses.stream().filter(filterNot(prefix, "domain")).collect(Collectors.toList());
		for(ClassInfo ci : infra) {
			final Class clazz = ci.load();
			nodes.put(clazz.getName(), node(clazz.getSimpleName()));
			graph = graph.with(nodes.get(clazz.getName()));
		}
		
		infra = allClasses.stream().filter(filterNot(prefix, "domain")).collect(Collectors.toList());

		List<ClassInfo> classes = new ArrayList<>();
		classes.addAll(infra);
		classes.addAll(domain);
		for(ClassInfo ci : classes) {
			final Class clazz = ci.load();
			// API
			Graph target = graph();
			boolean link = false;
			for (Field field : clazz.getDeclaredFields()) {
				final Class<?> type = field.getType();
				if (!type.isPrimitive()) {
					if(nodes.get(type.getName()) != null && nodes.get(clazz.getName()) != null) {
						target = target.with(nodes.get(type.getName()));
						link = true;
					}
				}
			}
			if(link)
				graph = graph.with(nodes.get(clazz.getName()).link(to(target)));

			// SPI
			target = graph();
			link = false;
			for (Class intf : clazz.getInterfaces()) {
				target = target.with(nodes.get(intf.getName()));
				link = true;
			}
			if(link)
				graph = graph.with(nodes.get(clazz.getName()).link(to(target).with(Style.DASHED, Arrow.NORMAL.open())));
		}

		// render into image
		final String template = readTestResource("viz-template.html");

		String title = "Living Diagram";
		final String content = fromGraphToDotString(graph);
		final String text = evaluate(template, title, content);
		write("", "livinggdiagram.html", text);
	}

	private String fromGraphToDotString(Graph graph) {
		return new Serializer((MutableGraph) graph).serialize();
	}
	
	private Graph buildEmptyGraph() {
		return graph()
				.directed()
				.graphAttr().with(
						RankDir.LEFT_TO_RIGHT, 
						Label.of("Hexagonal Architecture").
						locate(Label.Location.TOP),
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

	private Predicate<ClassInfo> filter(final String prefix, final String layer) {
		return new Predicate<ClassInfo>() {
			public boolean test(ClassInfo ci) {
				final boolean nameConvention = ci.getPackageName().startsWith(prefix)
						&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
						&& ci.getPackageName().endsWith("." + layer);
				return nameConvention;
			}

		};
	}

	private Predicate<ClassInfo> filterNot(final String prefix, final String layer) {
		return new Predicate<ClassInfo>() {
			public boolean test(ClassInfo ci) {
				final boolean nameConvention = ci.getPackageName().startsWith(prefix)
						&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
						&& !ci.getPackageName().endsWith("." + layer);
				return nameConvention;
			}

		};
	}
}
