/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.map.edge;

import java.awt.Color;
import java.io.IOException;

import org.freeplane.extension.IExtension;
import org.freeplane.io.IAttributeHandler;
import org.freeplane.io.INodeCreator;
import org.freeplane.io.INodeWriter;
import org.freeplane.io.ITreeWriter;
import org.freeplane.io.ReadManager;
import org.freeplane.io.WriteManager;
import org.freeplane.io.xml.n3.nanoxml.IXMLElement;
import org.freeplane.io.xml.n3.nanoxml.XMLElement;
import org.freeplane.main.Tools;
import org.freeplane.map.tree.NodeModel;
import org.freeplane.map.tree.NodeBuilder.NodeObject;

class EdgeBuilder implements INodeCreator, INodeWriter<IExtension> {
	public EdgeBuilder() {
	}

	public void completeNode(final Object parent, final String tag, final Object userObject) {
		/* attributes */
		if (parent instanceof NodeObject) {
			final NodeModel node = ((NodeObject) parent).node;
			if (userObject instanceof EdgeModel) {
				final EdgeModel edge = (EdgeModel) userObject;
				node.setEdge(edge);
			}
			return;
		}
	}

	protected EdgeModel createEdge(final NodeModel node) {
		return new EdgeModel();
	}

	public Object createNode(final Object parent, final String tag) {
		if (tag.equals("edge")) {
			return createEdge(null);
		}
		return null;
	}

	private void registerAttributeHandlers(final ReadManager reader) {
		reader.addAttributeHandler("edge", "STYLE", new IAttributeHandler() {
			public void parseAttribute(final Object userObject, final String value) {
				final EdgeModel edge = (EdgeModel) userObject;
				edge.setStyle(value.toString());
			}
		});
		reader.addAttributeHandler("edge", "COLOR", new IAttributeHandler() {
			public void parseAttribute(final Object userObject, final String value) {
				final EdgeModel edge = (EdgeModel) userObject;
				edge.setColor(Tools.xmlToColor(value.toString()));
			}
		});
		reader.addAttributeHandler("edge", "WIDTH", new IAttributeHandler() {
			public void parseAttribute(final Object userObject, final String value) {
				final EdgeModel edge = (EdgeModel) userObject;
				if (value.toString().equals(EdgeModel.EDGE_WIDTH_THIN_STRING)) {
					edge.setWidth(EdgeModel.WIDTH_THIN);
				}
				else {
					edge.setWidth(Integer.parseInt(value.toString()));
				}
			}
		});
	}

	/**
	 */
	public void registerBy(final ReadManager reader, final WriteManager writer) {
		reader.addNodeCreator("edge", this);
		registerAttributeHandlers(reader);
		writer.addExtensionNodeWriter(EdgeModel.class, this);
	}

	public void setAttributes(final String tag, final Object node, final IXMLElement attributes) {
	}

	public void writeContent(final ITreeWriter writer, final Object node, final IExtension extension)
	        throws IOException {
		final EdgeModel model = (EdgeModel) extension;
		final String style = model.getStyle();
		final Color color = model.getColor();
		final int width = model.getWidth();
		if (style != null || color != null || width != EdgeController.DEFAULT_WIDTH) {
			final XMLElement edge = new XMLElement();
			edge.setName("edge");
			boolean relevant = false;
			if (style != null) {
				edge.setAttribute("STYLE", style);
				relevant = true;
			}
			if (color != null) {
				edge.setAttribute("COLOR", Tools.colorToXml(color));
				relevant = true;
			}
			if (width != EdgeModel.WIDTH_PARENT) {
				if (width == EdgeModel.WIDTH_THIN) {
					edge.setAttribute("WIDTH", EdgeModel.EDGE_WIDTH_THIN_STRING);
				}
				else {
					edge.setAttribute("WIDTH", Integer.toString(width));
				}
				relevant = true;
			}
			if (relevant) {
				writer.addNode(model, edge);
			}
		}
	}
}
