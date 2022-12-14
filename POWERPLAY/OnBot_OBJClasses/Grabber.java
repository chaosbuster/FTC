package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.LED;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.State;
import org.firstinspires.ftc.robotcore.external.BlocksOpModeCompanion;
import org.firstinspires.ftc.robotcore.external.ExportToBlocks;


public class Grabber extends BlocksOpModeCompanion {

  static public Servo servoGrabber;

  static double scaledPositionGrab;       // Calculated in the init
  static double scaledPositionRelease;    // Calculated in the init
  
  private static DistanceSensor sensor2MDistance;
  
  private static boolean justGrabbed = false;

  static int distanceMinToBlockLower = 120;
  static int distanceMinToPickupCone = 125;
  static int distanceMaxToPickupCone = 155;
  static int distanceMaxToJunction = 170;
  static double curDistanceObjectToBackOfGrabber;
  
  static int moveWaitTime = 3000;  // Assuming 3 seconds is enough to move away from objects
  static ElapsedTime timegapForHoldingStates = new ElapsedTime();

  // Run states for our grabber
  static private enum State {
      START,
      WAITING_TO_GRAB, 
      HOLDING_OBJECT,
      WAITING_TO_RELEASE,
      NOT_HOLDING_OBJECT,
      END
  }
  
  static State grabberState;
  
  //=======================================================================

  @ExportToBlocks (
    heading = "Run Grabber Based on State",
    color = 255,
    comment = "Runs grabber based on its state.",
    tooltip = "States are changed by distance sensor."
  )
  /**
   * Runs an interation of the lift based on it's current state
   * Running the lift in a STATE mode allows for other actions to happen on the robot
   */
  public static void runGrabber() {
 
    // Grab the latest distance reading at the bottom of the lift to see if anything there
    curDistanceObjectToBackOfGrabber = sensor2MDistance.getDistance(DistanceUnit.MM);
    telemetry.addData("Distance Read: ", curDistanceObjectToBackOfGrabber);
    
    // Set our LEDs based on the distance readings
    if (curDistanceObjectToBackOfGrabber < distanceMinToBlockLower) {
      // We have something at the base of the lift
      telemetry.addData("GRABBER says: ", "Something's at the base of the lift.");
    } else if (curDistanceObjectToBackOfGrabber <= distanceMaxToPickupCone && curDistanceObjectToBackOfGrabber >= distanceMinToPickupCone) {
      // We have something within the 'PickupCone' distance
      telemetry.addData("GRABBER says: ", "Something's between Junction distance and PickupCone distance.");
    } else if (curDistanceObjectToBackOfGrabber <= distanceMaxToJunction && curDistanceObjectToBackOfGrabber > distanceMaxToPickupCone) {
      // We have something at the right Junction distance 
      telemetry.addData("GRABBER says: ", "Something's between Junction distance and PickupCone distance.");
    } else {
      // Default LEDs to not showing
      telemetry.addData("GRABBER says: ", "I'm farther than the maximum Junction distance.");
    }

    switch (grabberState) {
      
      case START: {
        // Let's get started!
        if (isGrabberClosed()) {
          openGrabber();
        }
        
        grabberState = State.NOT_HOLDING_OBJECT;

        break;
      }

      case WAITING_TO_GRAB: {
        
        // Make sure we are ready to grab with an OPEN grabber
        if (isGrabberClosed()) {
          openGrabber();
        }

        telemetry.addData("GRABBER State: ", "WAITING TO GRAB");
        
        // If lift is moving we are not going to grab our cone yet.
        if (Lift.isLiftMoving())
          break;
        
        // Check distance for LED change and grabbing cone
        if (curDistanceObjectToBackOfGrabber >= distanceMinToPickupCone && curDistanceObjectToBackOfGrabber <= distanceMaxToPickupCone ) {
          
          closeGrabber();
          
          grabberState = State.HOLDING_OBJECT;
          
          // Let's reset the timer that indicates how long ago we grabbed or released
          timegapForHoldingStates.reset();
          justGrabbed = true;
                  
        }
        break;
      }

      case HOLDING_OBJECT: {

        telemetry.addData("GRABBER State: ", "HOLDING OBJECT (Moving to mid release level)");
   
        // Let's give some time for the robot to move away from cone area
        // NOTE: If we don't do this the auto-grabber will release assuming 
        // we are in front of a junction, instead of a cone stack.
        if (timegapForHoldingStates.milliseconds() > moveWaitTime) {
          
          grabberState = State.WAITING_TO_RELEASE;
          justGrabbed = false;
          
        } else if (justGrabbed) {
          
          // Let's move the grabber to the mid-Junction level to be ready to release
          Lift.moveToLevelSpecified(1, true);
          
          justGrabbed = false;
          
        }
        
        break;
      }

      case WAITING_TO_RELEASE: {
        
        telemetry.addData("GRABBER State: ", "WAITING TO RELEASE");
        
        // If lift is moving we are not going to release our cone yet.
        if (Lift.isLiftMoving())
          break;
        
        // Check distance for when to release based on the range for where the function could be
        if (curDistanceObjectToBackOfGrabber < distanceMaxToJunction && curDistanceObjectToBackOfGrabber > distanceMaxToPickupCone) {
        
          // Release the object if notice something within the range
          openGrabber();
          
          // Changing our state
          grabberState = State.NOT_HOLDING_OBJECT;
          
          // Let's reset the timer that indicates how long ago we grabbed or released
          timegapForHoldingStates.reset();
                  
        }
        break;
      }

      case NOT_HOLDING_OBJECT: {

        telemetry.addData("GRABBER State: ", "NOT HOLDING OBJECT (NOT AUTO-MOVING)");
        
        // Let's give some time for the robot to move away from Junction
        // NOTE: If we don't do this the auto-grabber will grab assuming 
        // we are in front of a cone, instead of a junction.
        if (timegapForHoldingStates.milliseconds() > moveWaitTime) {
          
          // Let's move the grabber to the lowest grabbing level to be ready for next cone grab
          // NOT AUTOMATING THE LOWERING OF THE LIFT AFTER RELEASING.  
          // CONES CAN ACCIDENTLY GET UNDER THE GRABBER.
          // Lift.moveToLevelSpecified(0, true);

          grabberState = State.WAITING_TO_GRAB;
        }
        
        break;
      }
      
      case END: {
        break;
      }
    }

  }
  
  @ExportToBlocks (
    heading = "Grabber",
    color = 255,
    comment = "Initialize variables for our grabber.",
    tooltip = "Initialize variables for our grabber.",
    parameterLabels = {"Grabber Servo Name", "2M Distance Sensor Name"}
  )
   /** Initialize variables for our grabber:
    *    > Hardware map handle for motors and sensors
    */
   public static void initGrabber(String GrabberServoName, String sensor2MDistanceName) {
      
      // Let's get a hardware handle on the servo for the grabber
      // The string provided should be the same as the name in the active hardware configuration 
      servoGrabber = hardwareMap.get(Servo.class, GrabberServoName);
      
      // Prototyped values of servo positions for associated grabbing actions
      double positionGrabberMinScale = 100;
      double positionGrabberMaxScale = 200;
      double positionGrabberMaxGrip = 150;
      double positionGrabberGoodGrip = 152;  // Initially was 154
      double positionGrabberTouchSides = 185;
      double positionGrabberClearSides = 182;
      double positionGrabberRelease = 165;  // Originally 170
    
      // The two key servo positions that will be used 
      scaledPositionGrab = (positionGrabberGoodGrip - positionGrabberMinScale) / (positionGrabberMaxScale - positionGrabberMinScale);
      scaledPositionRelease = (positionGrabberRelease - positionGrabberMinScale) / (positionGrabberMaxScale - positionGrabberMinScale);
      
      // Let's get a hardware handle on our distance sensor at the bottom of our lift
      sensor2MDistance = hardwareMap.get(DistanceSensor.class, sensor2MDistanceName);
      
        // Grab the current distance reading 
      curDistanceObjectToBackOfGrabber = sensor2MDistance.getDistance(DistanceUnit.MM);
      telemetry.addData("Distance Read: ", curDistanceObjectToBackOfGrabber);
 
      // Set our grabber's state to a START state.
      grabberState = State.START;
      
  }

  @ExportToBlocks (
    heading = "Grabber",
    color = 255,
    comment = "Returns whether the grabber is closed.",
    tooltip = "Returns a TRUE if grabber is closed."
  )
  /**
   * Determines whether the grabber is closed and returns:
   *    TRUE Grabber IS closed
   *    FALSE Grabber is NOT closed
   */
  public static boolean isGrabberClosed() {
    
    double servoPosition = servoGrabber.getPosition();
    
    // Check to see if grabber servo position is close enough to the close set position
    if (Math.abs(servoPosition - scaledPositionGrab) < 0.05) 
      return true;
    else
      return false;
    
  }  // end method isGrabberClosed()

  @ExportToBlocks (
    heading = "Grabber",
    color = 255,
    comment = "Action to close grabber and grab.",
    tooltip = "Action to close grabber and grab."
  )
  /**
   * Closing servo/grabber enough to hold the item
   */
  public static void closeGrabber() {   
    
    // Close the grabber
    servoGrabber.setPosition(scaledPositionGrab);  
    
    // Change our state
    grabberState = State.HOLDING_OBJECT;
          
    // Let's reset the timer that indicates how long ago we grabbed or released
    timegapForHoldingStates.reset();
    justGrabbed = true;

  }  // end method closeGrabber()

  @ExportToBlocks (
    heading = "Grabber",
    color = 255,
    comment = "Action to open grabber and release.",
    tooltip = "Action to open grabber and release."
  )
  /**
   * Opening servo/grabber enough to release the item
   */
  public static void openGrabber() {  
    
    // Open the grabber
    servoGrabber.setPosition(scaledPositionRelease);
    
    // Changing our state
    grabberState = State.NOT_HOLDING_OBJECT;
          
    // Let's reset the timer that indicates how long ago we grabbed or released
    timegapForHoldingStates.reset();
    justGrabbed = false;
                  
  }  // end method openGrabber()
  
}  // end class Grabber
