package org.ndx.agile.architecture.example;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;

import org.ndx.agile.architecture.base.ArchitectureModelProvider;
import org.ndx.agile.architecture.base.enhancers.ModelElementKeys;
import org.ndx.agile.architecture.tickets.ADRExtractor;

import com.structurizr.Workspace;
import com.structurizr.io.plantuml.C4PlantUMLWriter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.Location;
import com.structurizr.model.Model;
import com.structurizr.model.Person;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.model.Tags;
import com.structurizr.view.ComponentView;
import com.structurizr.view.ContainerView;
import com.structurizr.view.DeploymentView;
import com.structurizr.view.Shape;
import com.structurizr.view.Styles;
import com.structurizr.view.SystemContextView;
import com.structurizr.view.ViewSet;

@ApplicationScoped
public class Architecture implements ArchitectureModelProvider {

	/**
	 * Creates the workspace object and add in it both the architecture components
	 * AND the views used to display it
	 * 
	 * @return
	 */
	public Workspace describeArchitecture() {
		Workspace workspace = new Workspace("Getting Started", "This is a model of my software system.");
		Model model = workspace.getModel();

		/////////////////////////////////////////////////////////////////////////////////////////
		Person waiting = model.addPerson("Waiting person", "Someone waiting for his transport.");
		Person inTrain = model.addPerson("Person in transport", "Someone already in the transport.");
		SoftwareSystem kafkatrain = model.addSoftwareSystem("kafkatrain", "Crowd-sourced transport timetable prediction system");
		kafkatrain.addProperty(ModelElementKeys.SCM_PROJECT, "https://github.com/Riduidel/snowcamp-2019");
		kafkatrain.addProperty(ADRExtractor.AGILE_ARCHITECTURE_TICKETS_PROJECT, "https://github.com/Riduidel/agile-architecture-documentation-example/");
		waiting.uses(kafkatrain, "See train delay");
		inTrain.uses(kafkatrain, "Informs application that train is running");
		SoftwareSystem navitia = model.addSoftwareSystem(Location.External, "navitia", "Official train time-table");
		SoftwareSystem sncfLocation = model.addSoftwareSystem(Location.External, "SNCF geolocation", "SNCF train real-time location");
		kafkatrain.uses(navitia, "Get official time table");
		kafkatrain.uses(sncfLocation, "Get real-time train position");

		/////////////////////////////////////////////////////////////////////////////////////////
		Container sncfReader = kafkatrain.addContainer("sncf-reader", "Read timetables from navitia, despite its name", "Java/Vert.x");
		sncfReader.addProperty(ModelElementKeys.SCM_PROJECT, "https://github.com/Riduidel/snowcamp-2019");
		sncfReader.addProperty(ModelElementKeys.SCM_PATH, "src/build/reference/sncf-reader");
		Container kafka = kafkatrain.addContainer("Streaming platform", "A platform to ingest timetables at the right speed", "Kafka");
		kafka.addProperty(C4PlantUMLWriter.C4_ELEMENT_TYPE, C4PlantUMLWriter.Type.Db.toString());
		sncfReader.uses(kafka, "Send timetables in our format");
		sncfReader.uses(navitia, "Read timetables")
			.addProperty(C4PlantUMLWriter.C4_LAYOUT_DIRECTION, C4PlantUMLWriter.Directions.Left.toString());
		Container elastic = kafkatrain.addContainer("Search engine", "A search engine allowing geographic queries", "Elastic");
		elastic.addProperty(C4PlantUMLWriter.C4_ELEMENT_TYPE, C4PlantUMLWriter.Type.Db.toString());
		Container kafkaConnect = kafkatrain.addContainer("Data sink", "A tool to push events in search engine", "Kafka Connect");
		kafkaConnect.uses(kafka, "Receives timetables");
		kafkaConnect.uses(elastic, "Sends timetables");
		Container webUI = kafkatrain.addContainer("web-ui", "Web UI allowing interaction with application", "Javascript/Node");
		webUI.addProperty(ModelElementKeys.SCM_PROJECT, "https://github.com/Riduidel/snowcamp-2019");
		webUI.addProperty(ModelElementKeys.SCM_PATH, "src/build/reference/web-ui");
		webUI.uses(elastic, "Read timetables");
		inTrain.uses(webUI, "Enter transport delay");
		webUI.delivers(waiting, "Get transport delay");
		Container jenkinsX = kafkatrain.addContainer("DevOps pipeline", "DevOps pipeline manager", "Jenkins-X");

		/////////////////////////////////////////////////////////////////////////////////////////
		Component sncfReaderVertice = sncfReader.addComponent("SncfReader", "Verticle connecting to Navitia");
		Component kafkaWriterVerticle = sncfReader.addComponent("KafkaWriter", "Verticle connecting to Kafka");
		Component eventBus = sncfReader.addComponent("Vertex event bus", "Core of async mechanisms in Vert.x");
		sncfReaderVertice.uses(navitia, "Read time tables")
			.addProperty(C4PlantUMLWriter.C4_LAYOUT_DIRECTION, C4PlantUMLWriter.Directions.Left.toString());
		sncfReaderVertice.uses(eventBus, "Propagates splitted time tables to event bus")
			.addProperty(C4PlantUMLWriter.C4_LAYOUT_DIRECTION, C4PlantUMLWriter.Directions.Right.toString());
		eventBus.uses(kafkaWriterVerticle, "Notifies writer")
			.addProperty(C4PlantUMLWriter.C4_LAYOUT_DIRECTION, C4PlantUMLWriter.Directions.Right.toString());
		kafkaWriterVerticle.uses(kafka, "Sends message")
			.addProperty(C4PlantUMLWriter.C4_LAYOUT_DIRECTION, C4PlantUMLWriter.Directions.Right.toString());

		/////////////////////////////////////////////////////////////////////////////////////////
		ViewSet views = workspace.getViews();
		SystemContextView contextView = views.createSystemContextView(kafkatrain, "SystemContext",
				"Systems involved in train prediction");
		contextView.addAllSoftwareSystems();
		contextView.addAllPeople();
		
		ContainerView kafkaTrainContainers = views.createContainerView(kafkatrain, "kafkatrain.containers", "Kafkatrain containers");
		kafkaTrainContainers.addAllContainersAndInfluencers();
		
		ComponentView sncfReaderComponentsView = views.createComponentView(sncfReader, "sncfReader.components", "Components of SNCF Reader");
		sncfReaderComponentsView.addAllComponents();
		sncfReaderComponentsView.add(kafka);
		sncfReaderComponentsView.add(navitia);
		
		DeploymentView deployment = views.createDeploymentView(kafkatrain, "deployment", "Deployment of Kafkatrain on Kubernetes");
		DeploymentNode k8s = model.addDeploymentNode("GKE", "GKE Kubernetes cluster", "Kubernetes");
		k8s.add(jenkinsX);
		k8s.add(kafka);
		k8s.add(elastic);
		DeploymentNode namespace = k8s.addDeploymentNode("Prod namespace", "Production namespace for our application", "Kubernetes namespace");
		namespace.add(sncfReader);
		namespace.add(webUI);
		deployment.add(k8s);

		Styles styles = views.getConfiguration().getStyles();
//		styles.addElementStyle(Tags.SOFTWARE_SYSTEM).background("#1168bd").color("#ffffff");
		styles.addElementStyle(Tags.PERSON).background("#08427b").color("#ffffff").shape(Shape.Person);
		return workspace;
	}

}
