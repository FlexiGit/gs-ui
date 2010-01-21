package org.graphstream.ui.j2dviewer.renderer

import java.awt.{Graphics, Graphics2D, Font, Color, RenderingHints}
import java.awt.event.{ActionListener, ActionEvent}
import java.awt.geom.{Point2D}
import javax.swing.{JComponent, JPanel, BorderFactory, JTextField, JButton, SwingConstants, ImageIcon}
import javax.swing.border.Border
import org.graphstream.ui.graphicGraph.{GraphicElement, GraphicNode, GraphicSprite, StyleGroup}
import org.graphstream.ui.graphicGraph.stylesheet.{Values, StyleConstants}
import org.graphstream.ui.j2dviewer.J2DGraphRenderer
import org.graphstream.ui.j2dviewer.util.{Camera, FontCache, ImageCache}
import org.graphstream.ui.j2dviewer.renderer.shape._

/**
 * Renderer for nodes and sprites represented as Swing components.
 */
class JComponentRenderer( styleGroup:StyleGroup, val mainRenderer:J2DGraphRenderer ) extends StyleRenderer( styleGroup ) {
// Attribute

	/** The size of components. */
	protected var size:Values = null
	
	/** The size in PX of components. */
	protected var width:Int = 0
	
	/** The size in PX of components. */
 	protected var height:Int = 0
	
	/** Association between Swing components and graph elements. */
	protected val compToElement = new scala.collection.mutable.HashMap[JComponent,ComponentElement]

	/** The potential shadow. */
	protected var shadow:SquareShape = null
 
	protected var antialiasSetting:AnyRef = null

// Command
  
	protected def setupRenderingPass( g:Graphics2D, camera:Camera, forShadow:Boolean ) {
		val metrics = camera.metrics
		
		size   = group.getSize
		width  = metrics.lengthToPx( size, 0 ).toInt
		height = if( size.size() > 1 ) metrics.lengthToPx( size, 1 ).toInt else width
  
		if( group.getShadowMode != StyleConstants.ShadowMode.NONE )
		     shadow = new SquareShape
		else shadow = null
		
		antialiasSetting = g.getRenderingHint( RenderingHints.KEY_ANTIALIASING )
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF )
	}
 
	override protected def endRenderingPass( g:Graphics2D, camera:Camera, forShadow:Boolean ) {
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, antialiasSetting )
	} 
	
	protected def pushStyle( g:Graphics2D, camera:Camera, forShadow:Boolean ) {
		if( shadow != null ) {
		  	shadow.configure( g, group, camera, null )
		  	shadow.size( group, camera )
		}
	}
	
	protected def pushDynStyle( g:Graphics2D, camera:Camera, element:GraphicElement ) {
	}
	
	protected def renderElement( g:Graphics2D, camera:Camera, element:GraphicElement ) {
		val ce = getOrEquipWithJComponent( element )

		ce.setVisible( true )
		ce.updatePosition( camera )
		ce.updateLabel

		if( ce.init == false )
		     checkStyle( camera, ce, true )
		else if( group.hasEventElements )
		     checkStyle( camera, ce, ! hadEvents )	// hadEvents allows to know if we just
		else checkStyle( camera, ce, hadEvents )	// changed the style due to an event	
	}												// and therefore must change the style.
													
	protected def renderShadow( g:Graphics2D, camera:Camera, element:GraphicElement ) {
		if( shadow != null ) {
			shadow.position( element.getX, element.getY )
			shadow.renderShadow( g, camera )
		}
	}
 
	protected def elementInvisible( g:Graphics2D, camera:Camera, element:GraphicElement ) {
		getOrEquipWithJComponent( element ).setVisible( false )
	}
 
// Utility
	
 	def unequipElement( element:GraphicElement ) {
		compToElement.get( element.getComponent.asInstanceOf[JComponent] ) match {
			case e:ComponentElement => { e.detach }
			case _                  => {}
		}
	}

	/**
	 * Get the pair (swing component, graph element) corresponding to the given element. If the
	 * element is not yet associated with a Swing component, the binding is done.
	 */
	protected def getOrEquipWithJComponent( element:GraphicElement ):ComponentElement = {
		import StyleConstants.JComponents._ 

		val component = element.getComponent.asInstanceOf[JComponent]
		var ce:ComponentElement = null
		
		if( component == null ) {
			group.getJComponent match {
				case BUTTON     => ce = new ButtonComponentElement( element, new JButton( "" ) )
				case TEXT_FIELD => ce = new TextFieldComponentElement( element, new JTextField( "" ) )
				case PANEL      => throw new RuntimeException( "panel not yet available" )
				case _          => throw new RuntimeException( "WTF ?!?" )
			}
			
			if( ce != null )
				compToElement.put( ce.jComponent, ce )
		} else {
			ce = compToElement.get( component ).get
		}
		
		ce
	}

	protected def checkStyle( camera:Camera, ce:ComponentElement, force:Boolean ) {
		if( force ) {
			ce.checkIcon( camera )
			ce.checkBorder( camera, force )
			ce.setFill
			ce.setTextAlignment
			ce.setTextFont
		}
	}
 
// Nested classes
 
	/**
	 * Represents the link between a JComponent and a GraphicElement.
	 * 
	 * Each of these component elements receive the action events of their button/text-field (for panel
	 * the user is free to do whatever he wants). They are in charge of adding and removing the
	 * component in the rendering surface, etc.
	 * 
	 * These elements also allow to push and remove the style to Swing components. We try to do this
	 * only when the style potentially changed, not at each redraw.
	 */
 	abstract class ComponentElement( val element:GraphicElement ) extends JPanel {
 	// Attribute
 	  
		/** Set to true if the element is not yet initialised with its style. */
		var init = false

	// Construction
 	
		setLayout( null )	// No layout in this panel, we set the component bounds ourselves.
 		mainRenderer.renderingSurface.add( this )
  
	// Access

		/** The Swing Component. */
		def jComponent:JComponent
	
		/** Set of reset the fill mode and colour for the Swing component. */
		def setFill() {
//			setBackground( group.getFillColor( 0 ) )
//			setOpaque( true )
//			if( group.getFillMode == StyleConstants.FillMode.PLAIN )
//				jComponent.setBackground( group.getFillColor( 0 ) )
		}
	
		/** Set or reset the text alignment for the Swing component. */
		def setTextAlignment()
		
		/** Set or reset the text font size, style and colour for the Swing component. */
		def setTextFont()
		
		/** Set or reset the label of the component. */
		def updateLabel()
	
		def setBounds( x:Int, y:Int, width:Int, height:Int, camera:Camera  ) {
			setBounds( x, y, width, height )
			
			var borderWidth:Int = 0
			
			if( group.getStrokeMode != StyleConstants.StrokeMode.NONE && group.getStrokeWidth().value > 0 )
				borderWidth = camera.metrics.lengthToPx( group.getStrokeWidth ).toInt

			jComponent.setBounds( borderWidth, borderWidth, width-(borderWidth*2), height-(borderWidth*2) )
		}
	
		/**
		 * Detach the Swing component from the graph element, remove the Swing component from its
		 * Swing container and remove any listeners on the Swing component. The ComponentElement
		 * is not usable after this.
		 */
		def detach {
			mainRenderer.renderingSurface.remove( this );
		}

		/**
		 * Check the swing component follows the graph element position.
		 * @param camera The transformation from GU to PX.
		 */
		def updatePosition( camera:Camera ) {
			element match {
				case e:GraphicNode   => positionNodeComponent(   element.asInstanceOf[GraphicNode],   camera )
				case e:GraphicSprite => positionSpriteComponent( element.asInstanceOf[GraphicSprite], camera )
				case _               => throw new RuntimeException( "WTF ?" )
			}
		}

	// Custom painting
	
		override def paint( g:Graphics ) {
			paintComponent( g )	// XXX Remove this ??? XXX
			paintBorder( g )
			paintChildren( g )
		}
		
	// Command -- Utility, positioning
		
		protected def positionNodeComponent( node:GraphicNode, camera:Camera ) {
			val pos = camera.transform( node.getX, node.getY )
	
			setBounds( (pos.x-(width/2)).toInt, (pos.y-(height/2)).toInt, width, height, camera )
		}
		
		protected def positionSpriteComponent( sprite:GraphicSprite, camera:Camera ) {
			val pos = camera.getSpritePosition( sprite, new Point2D.Float, StyleConstants.Units.PX )
	
			setBounds( (pos.x-(width/2)).toInt, (pos.y-(height/2)).toInt, width, height, camera )
		}

	// Command -- Utility, applying CSS style to Swing components
		
		def checkBorder( camera:Camera, force:Boolean ) {
			if( force ) {
				if( group.getStrokeMode != StyleConstants.StrokeMode.NONE && group.getStrokeWidth().value > 0 )
			         setBorder( createBorder( camera ) )
				else setBorder( null )
			} else {
				updateBorder( camera )
			}
		}
		
		protected def createBorder( camera:Camera ):Border = {
			import StyleConstants.StrokeMode._

			val width:Int = camera.metrics.lengthToPx( group.getStrokeWidth ).toInt
			
			group.getStrokeMode match {
				case PLAIN  => BorderFactory.createLineBorder( group.getStrokeColor( 0 ), width )
				case DOTS   => throw new RuntimeException( "TODO create dots and dashes borders for component to respect stroke-mode." );
				case DASHES => throw new RuntimeException( "TODO create dots and dashes borders for component to respect stroke-mode." );
				case _      => null
			}
		}
		
		protected def updateBorder( camera:Camera ) {}
		
		def checkIcon( camera:Camera )
	}
  
    class TextFieldComponentElement( element:GraphicElement, val component:JTextField ) extends ComponentElement( element ) with ActionListener {
	// Construction

		element.setComponent( component )
		component.addActionListener( this )
		add( component )

	// Command
  
		override def detach() {
			super.detach
			component.removeActionListener( this )
			remove( component )
			element.setComponent( null )
	
			//component = null
			//element   = null
		}
	
		def actionPerformed( e:ActionEvent ) {
			element.label = component.asInstanceOf[JTextField].getText
			element.setAttribute( "ui.label", element.label )
			element.setAttribute( "ui.clicked" )
	    }
	
		override def jComponent:JComponent = component
		
		override def setTextAlignment() {
			import StyleConstants.TextAlignment._
			group.getTextAlignment match {
				case ABOVE    => component.setHorizontalAlignment( SwingConstants.CENTER )
				case UNDER    => component.setHorizontalAlignment( SwingConstants.CENTER )
				case ALONG    => component.setHorizontalAlignment( SwingConstants.CENTER )
				case JUSTIFY  => component.setHorizontalAlignment( SwingConstants.CENTER )
				case CENTER   => component.setHorizontalAlignment( SwingConstants.CENTER )
				case AT_RIGHT => component.setHorizontalAlignment( SwingConstants.RIGHT )
				case RIGHT    => component.setHorizontalAlignment( SwingConstants.RIGHT )
				case AT_LEFT  => component.setHorizontalAlignment( SwingConstants.LEFT )
				case LEFT     => component.setHorizontalAlignment( SwingConstants.LEFT )
				case _        => {}
			}
		}
		
		override def setTextFont() {
			var font = if( ! group.getTextFont.equals( "default" ) )
			                FontCache.getFont( group.getTextFont, group.getTextStyle, group.getTextSize.value.toInt )
			           else FontCache.getDefaultFont( group.getTextStyle, group.getTextSize.value.toInt )
			
			component.setFont( font )
			component.setForeground( group.getTextColor( 0 ) )
		}
		
		override def updateLabel() {
			if( ! component.hasFocus() )
				component.setText( element.getLabel )
		}
	
		override def checkIcon( camera:Camera ) { /* NOP */ }
	}
    
    class ButtonComponentElement( element:GraphicElement, val component:JButton ) extends ComponentElement( element ) with ActionListener {
	// Construction
    
		element.setComponent( component )
		component.addActionListener( this )
		add( component )
      
    // Commands
    
		override def detach() {
			super.detach
			component.removeActionListener( this )
			remove( component )
			element.setComponent( null )
	
//			component = null;
//			element   = null;
		}
	
		def actionPerformed( e:ActionEvent ) {
			element.label = component.getText
			element.setAttribute( "ui.label", element.label )
			element.setAttribute( "ui.clicked" )
			element.myGraph.setAttribute( "ui.clicked", element.getId )
	    }
	
		override def jComponent:JComponent = component
	
		override def setTextAlignment() {
			import StyleConstants.TextAlignment._
			group.getTextAlignment match {
				case ALONG    => component.setHorizontalAlignment( SwingConstants.CENTER )
				case JUSTIFY  => component.setHorizontalAlignment( SwingConstants.CENTER )
				case CENTER   => component.setHorizontalAlignment( SwingConstants.CENTER )
				case AT_RIGHT => component.setHorizontalAlignment( SwingConstants.RIGHT )
				case RIGHT    => component.setHorizontalAlignment( SwingConstants.RIGHT )
				case AT_LEFT  => component.setHorizontalAlignment( SwingConstants.LEFT )
				case LEFT     => component.setHorizontalAlignment( SwingConstants.LEFT )
				case ABOVE    => component.setVerticalAlignment( SwingConstants.TOP )
				case UNDER    => component.setVerticalAlignment( SwingConstants.BOTTOM )
				case _        => {}
			}
		}
	
		override def setTextFont() {
			val font = if( ! group.getTextFont().equals( "default" ) )
			     FontCache.getFont( group.getTextFont, group.getTextStyle, group.getTextSize.value.toInt )
			else FontCache.getDefaultFont( group.getTextStyle, group.getTextSize().value.toInt )
			
			component.setFont( font )
			component.setForeground( group.getTextColor( 0 ) )
		}
		
		override def updateLabel() {
			val label = element.getLabel
			
			if( label != null )
				component.setText( label )
		}
	
		override def checkIcon( camera:Camera ) {
			import StyleConstants.IconMode._
		  
			if( group.getIconMode != StyleConstants.IconMode.NONE ) {
				val url   = group.getIcon
				val image = ImageCache.loadImage( url ).get
				
				if( image != null ) {
					component.setIcon( new ImageIcon( image ) )
					
					group.getIconMode match {
						case AT_LEFT  => { component.setHorizontalTextPosition( SwingConstants.RIGHT );  component.setVerticalTextPosition( SwingConstants.CENTER ) }
						case AT_RIGHT => { component.setHorizontalTextPosition( SwingConstants.LEFT  );  component.setVerticalTextPosition( SwingConstants.CENTER ) }
						case ABOVE    => { component.setHorizontalTextPosition( SwingConstants.CENTER ); component.setVerticalTextPosition( SwingConstants.BOTTOM ) }
						case UNDER    => { component.setHorizontalTextPosition( SwingConstants.CENTER ); component.setVerticalTextPosition( SwingConstants.TOP )    }
						case _        => { throw new RuntimeException( "unknown image mode" ) }
					}
				}
			}
		}
    }
}